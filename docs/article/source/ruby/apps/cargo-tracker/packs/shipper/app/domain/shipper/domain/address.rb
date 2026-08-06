# frozen_string_literal: true

module Shipper
  module Domain
    # 住所（オプション、最大 500 文字）。
    Address = Data.define(:value) do
      MAX_LENGTH = 500

      def initialize(value:)
        if value && value.length > MAX_LENGTH
          raise ArgumentError, "住所は #{MAX_LENGTH} 文字以内です"
        end

        super(value: value)
      end

      def to_s = value.to_s
    end
  end
end
