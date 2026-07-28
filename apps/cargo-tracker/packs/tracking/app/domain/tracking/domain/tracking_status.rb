# frozen_string_literal: true

module Tracking
  module Domain
    # 輸送状態（TrackingStatus）。荷役・手動更新で遷移する（IT5 対象値）。
    # 値等価の不変オブジェクト。
    class TrackingStatus
      NOT_RECEIVED = "NOT_RECEIVED"
      RECEIVED = "RECEIVED"
      LOADED = "LOADED"
      ONBOARD_CARRIER = "ONBOARD_CARRIER"
      UNLOADED = "UNLOADED"
      AWAITING_CLAIM = "AWAITING_CLAIM"
      CLAIMED = "CLAIMED"

      VALUES = [
        NOT_RECEIVED, RECEIVED, LOADED, ONBOARD_CARRIER, UNLOADED, AWAITING_CLAIM, CLAIMED
      ].freeze

      attr_reader :value

      def self.initial = new(value: NOT_RECEIVED)

      def initialize(value:)
        raise ArgumentError, "輸送状態が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      def not_received? = value == NOT_RECEIVED
      def claimed? = value == CLAIMED

      def ==(other) = other.is_a?(TrackingStatus) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
