# frozen_string_literal: true

module Booking
  module Domain
    # 貨物種別。GENERAL（一般）/ HAZARDOUS（危険物）/ REFRIGERATED（冷凍・冷蔵）。
    CargoType = Data.define(:value) do
      GENERAL = "GENERAL"
      HAZARDOUS = "HAZARDOUS"
      REFRIGERATED = "REFRIGERATED"
      VALUES = [ GENERAL, HAZARDOUS, REFRIGERATED ].freeze

      def initialize(value:)
        raise ArgumentError, "貨物種別が不正です: #{value}" unless VALUES.include?(value)

        super(value: value)
      end

      def general? = value == GENERAL
      def hazardous? = value == HAZARDOUS
      def refrigerated? = value == REFRIGERATED
      def to_s = value
    end
  end
end
