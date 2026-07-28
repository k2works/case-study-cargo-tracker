# frozen_string_literal: true

module Shipper
  module Domain
    # 荷主種別。INDIVIDUAL（個人）/ CORPORATE（法人）。
    ShipperType = Data.define(:value) do
      INDIVIDUAL = "INDIVIDUAL"
      CORPORATE = "CORPORATE"
      VALUES = [ INDIVIDUAL, CORPORATE ].freeze

      def initialize(value:)
        raise ArgumentError, "荷主種別が不正です: #{value}" unless VALUES.include?(value)

        super(value: value)
      end

      def individual? = value == INDIVIDUAL
      def corporate? = value == CORPORATE
      def to_s = value
    end
  end
end
