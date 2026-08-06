# frozen_string_literal: true

module Booking
  module Domain
    # 寸法（長さ・幅・高さ、cm）。オプション（nil 許容）。
    Dimensions = Data.define(:length, :width, :height) do
      def initialize(length:, width:, height:)
        [ length, width, height ].each do |v|
          raise ArgumentError, "寸法は 0 より大きい必要があります" if v && v.to_f <= 0
        end
        super
      end
    end
  end
end
