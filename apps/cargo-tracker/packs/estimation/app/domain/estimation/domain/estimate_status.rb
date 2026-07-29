# frozen_string_literal: true

module Estimation
  module Domain
    # 見積状態（EstimateStatus）。CREATED（作成済）/ EXPIRED（期限切れ）の不変値オブジェクト（US01）。
    class EstimateStatus
      CREATED = "CREATED"
      EXPIRED = "EXPIRED"

      VALUES = [ CREATED, EXPIRED ].freeze

      attr_reader :value

      def self.initial = new(value: CREATED)

      def initialize(value:)
        raise ArgumentError, "見積状態が不正です: #{value}" unless VALUES.include?(value)

        @value = value
        freeze
      end

      def created? = value == CREATED

      def ==(other) = other.is_a?(EstimateStatus) && other.value == value
      alias eql? ==
      def hash = value.hash
      def to_s = value
    end
  end
end
