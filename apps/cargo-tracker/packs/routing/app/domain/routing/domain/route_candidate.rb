# frozen_string_literal: true

module Routing
  module Domain
    # 経路候補（一時計算値・非永続。ADR-0004）。US08 で算出する。
    # 永続化は Estimation Context（IT7）の責務。
    RouteCandidate = Data.define(:legs, :transit_days, :cost, :voyage_numbers, :fallback) do
      # legs: [{ from:, to:, voyage_number: }] の配列
      def initialize(legs:, transit_days:, cost:, voyage_numbers:, fallback: false)
        raise ArgumentError, "経路候補には 1 区間以上必要です" if Array(legs).empty?

        # 外部システムが文字列で返しても期限計算が壊れないよう所要日数を整数に正規化する。
        super(legs: legs, transit_days: transit_days.to_i, cost: cost,
              voyage_numbers: voyage_numbers, fallback: fallback)
      end

      def origin = legs.first[:from]
      def destination = legs.last[:to]
      def direct? = legs.size == 1
      def fallback? = fallback == true
    end
  end
end
