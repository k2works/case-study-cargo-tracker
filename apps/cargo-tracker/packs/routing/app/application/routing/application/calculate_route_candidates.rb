# frozen_string_literal: true

module Routing
  module Application
    # 経路候補算出ユースケース（US08・一時計算値・ADR-0004）。
    # 外部経路 ACL から候補を取得し、推奨順（直行便優先→所要日数昇順）に並べ、
    # 期限内に到達可能な候補がなければ通知する。
    class CalculateRouteCandidates
      Result = Struct.new(:candidates, :message, keyword_init: true) do
        def success?
          candidates.present?
        end
      end

      def initialize(routing_service:)
        @routing_service = routing_service
      end

      def call(origin:, destination:, arrival_deadline:, departure_date: nil)
        deadline = parse_date(arrival_deadline)
        depart = parse_date(departure_date) || Date.current

        request = Domain::ExternalCargoRoutingService::SearchRequest.new(
          origin: origin, destination: destination, arrival_deadline: deadline
        )
        candidates = @routing_service.search_routes(request)
        return Result.new(message: "条件を満たす経路候補がありません。条件を調整してください") if candidates.blank?

        # 期限内に到達可能な候補のみ（DATE 期限 vs 所要日数。日付単位で比較）
        in_deadline = candidates.select do |c|
          deadline.nil? || (depart + c.transit_days) <= deadline
        end
        if in_deadline.empty?
          return Result.new(message: "期限内に到達可能な経路がありません。到着期限または条件を調整してください")
        end

        Result.new(candidates: recommend(in_deadline))
      end

      private

      # 直行便を最優先、次に所要日数の昇順で並べる。
      def recommend(candidates)
        candidates.sort_by { |c| [ c.direct? ? 0 : 1, c.transit_days.to_i ] }
      end

      def parse_date(value)
        return value if value.is_a?(Date)
        return nil if value.blank?

        Date.parse(value.to_s)
      rescue ArgumentError
        nil
      end
    end
  end
end
