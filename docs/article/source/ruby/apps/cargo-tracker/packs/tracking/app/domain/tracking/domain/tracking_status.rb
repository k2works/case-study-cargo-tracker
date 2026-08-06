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
      EXCEPTION = "EXCEPTION"

      VALUES = [
        NOT_RECEIVED, RECEIVED, LOADED, ONBOARD_CARRIER, UNLOADED, AWAITING_CLAIM, CLAIMED, EXCEPTION
      ].freeze

      attr_reader :value

      # 荷役作業種別（RECEIVE/LOAD/UNLOAD/CLAIM）に対応する輸送状態。
      FROM_HANDLING = {
        "RECEIVE" => RECEIVED, "LOAD" => LOADED, "UNLOAD" => UNLOADED, "CLAIM" => CLAIMED
      }.freeze

      def self.initial = new(value: NOT_RECEIVED)

      # 荷役種別から対応する輸送状態を返す（該当なしは nil）。
      def self.for_handling(handling_type)
        value = FROM_HANDLING[handling_type.to_s]
        value && new(value: value)
      end

      def initialize(value:)
        raise ArgumentError, "輸送状態が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      def self.exception = new(value: EXCEPTION)

      def not_received? = value == NOT_RECEIVED
      def claimed? = value == CLAIMED
      def exception? = value == EXCEPTION

      def ==(other) = other.is_a?(TrackingStatus) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
