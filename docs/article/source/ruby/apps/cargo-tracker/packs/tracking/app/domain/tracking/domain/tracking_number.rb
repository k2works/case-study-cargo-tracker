# frozen_string_literal: true

require "securerandom"

module Tracking
  module Domain
    # 追跡番号（TRK- + 8 桁 16 進）。追跡活動を一意に識別する値オブジェクト。
    class TrackingNumber
      FORMAT = /\ATRK-[0-9A-F]{8}\z/

      attr_reader :value

      def self.generate
        new(value: "TRK-#{SecureRandom.hex(4).upcase}")
      end

      def initialize(value:)
        raise ArgumentError, "追跡番号の形式が不正です: #{value}" unless FORMAT.match?(value.to_s)

        @value = value
        freeze
      end

      def ==(other) = other.is_a?(TrackingNumber) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
