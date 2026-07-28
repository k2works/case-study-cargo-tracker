# frozen_string_literal: true

require "bigdecimal"

module Shipper
  module Domain
    # 割引率（法人荷主）。0.0000〜0.3000（0%〜30%）。
    DiscountRate = Data.define(:value) do
      RANGE = (BigDecimal("0")..BigDecimal("0.3")).freeze

      def initialize(value:)
        rate = BigDecimal(value.to_s)
        raise ArgumentError, "割引率は 0〜30% です" unless RANGE.cover?(rate)

        super(value: rate)
      end

      def percentage = (value * 100).to_i
      def to_s = value.to_s
    end
  end
end
