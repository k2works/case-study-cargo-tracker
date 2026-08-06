# frozen_string_literal: true

module Billing
  module Domain
    # 支払状態（PaymentStatus）。請求書の精算ライフサイクルを表す不変値オブジェクト（US23）。
    class PaymentStatus
      PENDING = "PENDING"
      CONFIRMED = "CONFIRMED"
      OVERDUE = "OVERDUE"
      REFUNDED = "REFUNDED"

      VALUES = [ PENDING, CONFIRMED, OVERDUE, REFUNDED ].freeze

      attr_reader :value

      def self.initial = new(value: PENDING)

      def initialize(value:)
        raise ArgumentError, "支払状態が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      def pending? = value == PENDING
      def confirmed? = value == CONFIRMED
      def overdue? = value == OVERDUE
      # 未精算（入金確認・料金調整の対象・PENDING or OVERDUE）。
      def unsettled? = pending? || overdue?

      def ==(other) = other.is_a?(PaymentStatus) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
