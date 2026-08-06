# frozen_string_literal: true

module Tracking
  module Domain
    # 追跡例外の種別（ExceptionType）。遅延・破損・紛失・税関保留を表す値オブジェクト。
    # 値等価の不変オブジェクト。LOST は上位管理者へのエスカレーションを要する（US20）。
    class ExceptionType
      DELAY = "DELAY"
      DAMAGE = "DAMAGE"
      LOST = "LOST"
      CUSTOMS_HOLD = "CUSTOMS_HOLD"

      VALUES = [ DELAY, DAMAGE, LOST, CUSTOMS_HOLD ].freeze

      attr_reader :value

      def initialize(value:)
        raise ArgumentError, "例外種別が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      # 紛失（LOST）は管理職へのエスカレーションを要する（US20 受入基準）。
      def escalation_required? = value == LOST

      def ==(other) = other.is_a?(ExceptionType) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
