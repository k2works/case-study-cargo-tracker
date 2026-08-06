# frozen_string_literal: true

module Handling
  module Domain
    # 荷役作業種別（RECEIVE/LOAD/UNLOAD/CLAIM）。値等価の不変オブジェクト。
    class HandlingType
      RECEIVE = "RECEIVE"
      LOAD = "LOAD"
      UNLOAD = "UNLOAD"
      CLAIM = "CLAIM"
      VALUES = [ RECEIVE, LOAD, UNLOAD, CLAIM ].freeze

      attr_reader :value

      def initialize(value:)
        raise ArgumentError, "荷役作業種別が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      # 積込・荷降しは航海番号が必須（どの航海での作業かを特定する）。
      def requires_voyage_number? = [ LOAD, UNLOAD ].include?(value)
      # 積込・荷降しは旅程（Itinerary）との照合対象。
      def route_bound? = [ LOAD, UNLOAD ].include?(value)
      def receive? = value == RECEIVE
      def load? = value == LOAD
      def claim? = value == CLAIM

      def ==(other) = other.is_a?(HandlingType) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
