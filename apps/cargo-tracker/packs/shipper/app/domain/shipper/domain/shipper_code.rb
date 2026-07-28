# frozen_string_literal: true

require "securerandom"

module Shipper
  module Domain
    # 荷主コード（業務識別子）。SHP- プレフィックス + 16 進 8 文字。
    ShipperCode = Data.define(:value) do
      FORMAT = /\ASHP-[0-9A-F]{8}\z/

      def initialize(value:)
        raise ArgumentError, "荷主コードの形式が不正です: #{value}" unless FORMAT.match?(value)

        super(value: value)
      end

      # 新規の荷主コードを自動生成する。
      def self.generate
        new(value: "SHP-#{SecureRandom.hex(4).upcase}")
      end

      def to_s = value
    end
  end
end
