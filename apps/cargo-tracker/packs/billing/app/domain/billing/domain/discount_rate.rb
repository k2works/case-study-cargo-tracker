# frozen_string_literal: true

module Billing
  module Domain
    # 割引率（DiscountRate）。0〜30%（0.0〜0.30）の不変値オブジェクト（US22・法人割引）。
    class DiscountRate
      MIN = BigDecimal("0")
      MAX = BigDecimal("0.30")

      attr_reader :rate

      def self.none = new(rate: MIN)

      # 割引パーセント（0〜30 の整数）から生成する（Shipper の discount_percentage 連携）。
      def self.from_percentage(percentage)
        new(rate: BigDecimal(percentage.to_s) / 100)
      end

      def initialize(rate:)
        value = rate.is_a?(BigDecimal) ? rate : BigDecimal(rate.to_s)
        raise ArgumentError, "割引率は 0〜30% の範囲です: #{value.to_f}" if value < MIN || value > MAX

        @rate = value
        freeze
      end

      # 割引後の残存係数（1 - 割引率）。
      def remaining_factor = BigDecimal("1") - rate

      def ==(other) = other.is_a?(DiscountRate) && other.rate == rate
      alias eql? ==
      def hash = rate.hash
    end
  end
end
