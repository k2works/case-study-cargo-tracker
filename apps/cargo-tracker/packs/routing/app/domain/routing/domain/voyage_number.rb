# frozen_string_literal: true

module Routing
  module Domain
    # 航海番号（Routing Context 固有の一意識別子）。
    VoyageNumber = Data.define(:value) do
      def initialize(value:)
        raise ArgumentError, "航海番号は必須です" if value.nil? || value.to_s.strip.empty?
        raise ArgumentError, "航海番号は 20 文字以内です" if value.to_s.length > 20

        super(value: value.to_s)
      end

      def to_s = value
    end
  end
end
