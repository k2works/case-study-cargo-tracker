# frozen_string_literal: true

module Booking
  module Domain
    # 予約状態（9 値の状態機械）。不正遷移は InvalidTransition を送出する。
    # 値等価の不変オブジェクト（PORO）。
    class BookingStatus
      class InvalidTransition < StandardError; end

      PRELIMINARY = "PRELIMINARY"
      ROUTE_REQUESTED = "ROUTE_REQUESTED"
      ROUTE_PROPOSED = "ROUTE_PROPOSED"
      CONFIRMED = "CONFIRMED"
      TRACKING_ISSUED = "TRACKING_ISSUED"
      IN_TRANSIT = "IN_TRANSIT"
      DELIVERED = "DELIVERED"
      SETTLED = "SETTLED"
      CANCELLED = "CANCELLED"

      VALUES = [
        PRELIMINARY, ROUTE_REQUESTED, ROUTE_PROPOSED, CONFIRMED,
        TRACKING_ISSUED, IN_TRANSIT, DELIVERED, SETTLED, CANCELLED
      ].freeze

      # 通常の進行順（各状態から次に進める状態）。
      FORWARD = {
        PRELIMINARY => ROUTE_REQUESTED,
        ROUTE_REQUESTED => ROUTE_PROPOSED,
        ROUTE_PROPOSED => CONFIRMED,
        CONFIRMED => TRACKING_ISSUED,
        TRACKING_ISSUED => IN_TRANSIT,
        IN_TRANSIT => DELIVERED,
        DELIVERED => SETTLED
      }.freeze

      # 差戻し遷移（US13・ルート変更）。ROUTE_PROPOSED から経路設計中へ戻す。
      BACKWARD = {
        ROUTE_PROPOSED => ROUTE_REQUESTED
      }.freeze

      # 終端（SETTLED/CANCELLED）と DELIVERED を除く任意状態からキャンセル可能。
      CANCELLABLE = (VALUES - [ SETTLED, CANCELLED, DELIVERED ]).freeze

      attr_reader :value

      def initialize(value:)
        raise ArgumentError, "予約状態が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      def self.initial
        new(value: PRELIMINARY)
      end

      # 指定状態へ遷移した新しい BookingStatus を返す。不正遷移は例外。
      def transition_to(target)
        unless can_transition_to?(target)
          raise InvalidTransition, "#{value} から #{target} へは遷移できません"
        end

        BookingStatus.new(value: target)
      end

      def can_transition_to?(target)
        return true if target == CANCELLED && CANCELLABLE.include?(value)

        FORWARD[value] == target || BACKWARD[value] == target
      end

      def preliminary? = value == PRELIMINARY
      def route_requested? = value == ROUTE_REQUESTED
      def route_proposed? = value == ROUTE_PROPOSED
      def confirmed? = value == CONFIRMED
      def cancelled? = value == CANCELLED

      def ==(other)
        other.is_a?(BookingStatus) && other.value == value
      end
      alias eql? ==

      def hash = value.hash
      def to_s = value
    end
  end
end
