# frozen_string_literal: true

module Billing
  module Domain
    # 割増（Surcharge）。基本料金への加算率を表す不変値オブジェクト（US21・燃油/危険物割増）。
    # DiscountRate とは独立の概念で、基本料金に対して加算される。
    class Surcharge
      FUEL = "FUEL"
      HAZARDOUS_HANDLING = "HAZARDOUS_HANDLING"

      TYPES = [ FUEL, HAZARDOUS_HANDLING ].freeze

      attr_reader :type, :rate

      def initialize(type:, rate:)
        raise ArgumentError, "割増種別が不正です: #{type}" unless TYPES.include?(type)

        @type = type
        @rate = rate.is_a?(BigDecimal) ? rate : BigDecimal(rate.to_s)
        freeze
      end

      # 基本料金に対する割増額を算出する。
      def apply(base_amount)
        base_amount.multiply(rate)
      end
    end
  end
end
