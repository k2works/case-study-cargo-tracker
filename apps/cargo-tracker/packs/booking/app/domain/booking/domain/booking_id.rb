# frozen_string_literal: true

require "securerandom"

module Booking
  module Domain
    # 予約番号（業務識別子）。BKG- プレフィックス + 16 進 8 文字。
    BookingId = Data.define(:value) do
      FORMAT = /\ABKG-[0-9A-F]{8}\z/

      def initialize(value:)
        raise ArgumentError, "予約番号の形式が不正です: #{value}" unless FORMAT.match?(value)

        super(value: value)
      end

      def self.generate
        new(value: "BKG-#{SecureRandom.hex(4).upcase}")
      end

      def to_s = value
    end
  end
end
