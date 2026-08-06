# frozen_string_literal: true

require "faraday"
require "json"

module Routing
  module Infrastructure
    # 外部経路システムの HTTP アダプタ（ExternalCargoRoutingService 実装・ADR-0004）。
    # Faraday で外部システムへ経路探索を依頼し、接続タイムアウト時は
    # LocalRoutingFallback（過去実績データ）へフォールバックする。
    class ExternalCargoRoutingClient < Domain::ExternalCargoRoutingService
      TIMEOUT_SECONDS = 5

      def initialize(base_url:, fallback: LocalRoutingFallback.new, connection: nil)
        @base_url = base_url
        @fallback = fallback
        @connection = connection
      end

      def search_routes(request)
        response = connection.post("/search") do |req|
          req.headers["Content-Type"] = "application/json"
          req.body = { origin: request.origin, destination: request.destination,
                       arrival_deadline: request.arrival_deadline.to_s }.to_json
        end
        raise Faraday::ServerError, response if response.status >= 500

        parse(response.body)
      rescue Faraday::Error, JSON::ParserError
        # タイムアウト・接続失敗・5xx・不正 JSON いずれも外部障害としてフォールバックする。
        @fallback.candidates_for(request)
      end

      private

      def connection
        @connection ||= Faraday.new(url: @base_url) do |f|
          f.options.timeout = TIMEOUT_SECONDS
          f.options.open_timeout = TIMEOUT_SECONDS
        end
      end

      def parse(body)
        data = body.is_a?(String) ? JSON.parse(body) : body
        Array(data["candidates"] || data[:candidates]).map do |c|
          c = c.transform_keys(&:to_s)
          legs = Array(c["legs"]).map { |l| l.transform_keys(&:to_sym) }
          Domain::RouteCandidate.new(
            legs: legs, transit_days: c["transit_days"], cost: c["cost"],
            voyage_numbers: legs.map { |l| l[:voyage_number] }, fallback: false
          )
        end
      end
    end
  end
end
