# frozen_string_literal: true

module Estimation
  module Domain
    # 経路候補（RouteCandidate）。見積に紐づく不変値オブジェクト（US01）。
    # 経由港・所要日数・概算料金・航海番号を保持する。Routing の一時候補を Estimation へ永続化する
    # （ADR-0004・IT7 で統合）。
    class RouteCandidate
      attr_reader :voyage_number, :transit_port, :transit_days, :estimated_cost, :rank

      def initialize(voyage_number:, transit_days:, estimated_cost:, transit_port: nil, rank: 0)
        raise ArgumentError, "航海番号は必須です" if voyage_number.to_s.strip.empty?
        raise ArgumentError, "所要日数は正である必要があります" if transit_days.to_i <= 0

        cost = estimated_cost.is_a?(BigDecimal) ? estimated_cost : BigDecimal(estimated_cost.to_s)
        raise ArgumentError, "概算料金は正である必要があります" if cost <= 0

        @voyage_number = voyage_number
        @transit_port = transit_port
        @transit_days = transit_days.to_i
        @estimated_cost = cost
        @rank = rank.to_i
        freeze
      end

      def ==(other)
        other.is_a?(RouteCandidate) && other.voyage_number == voyage_number &&
          other.transit_days == transit_days && other.estimated_cost == estimated_cost
      end
      alias eql? ==
      def hash = [ voyage_number, transit_days, estimated_cost ].hash
    end
  end
end
