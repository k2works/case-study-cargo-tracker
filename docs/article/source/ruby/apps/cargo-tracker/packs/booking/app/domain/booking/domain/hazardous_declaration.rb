# frozen_string_literal: true

module Booking
  module Domain
    # 危険物申告。危険物クラス・UN 番号・正式輸送品名（HAZARDOUS 時必須）。
    HazardousDeclaration = Data.define(:hazardous_class, :un_number, :proper_shipping_name) do
      def initialize(hazardous_class:, un_number:, proper_shipping_name:)
        raise ArgumentError, "危険物クラスは必須です" if blank?(hazardous_class)
        raise ArgumentError, "UN 番号は必須です" if blank?(un_number)
        raise ArgumentError, "正式輸送品名は必須です" if blank?(proper_shipping_name)

        super
      end

      private

      def blank?(value)
        value.nil? || value.to_s.strip.empty?
      end
    end
  end
end
