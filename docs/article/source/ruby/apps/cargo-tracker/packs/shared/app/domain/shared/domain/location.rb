# frozen_string_literal: true

module Shared
  module Domain
    # 位置情報（共有カーネル）。UN/LOCODE で識別される港湾・地点。
    # 全 Bounded Context で共有する（ADR-0001 Shared Domain）。
    Location = Data.define(:unlocode, :name) do
      FORMAT = /\A[A-Z0-9]{5}\z/

      def initialize(unlocode:, name:)
        raise ArgumentError, "UN/LOCODE は 5 文字の英大文字/数字です: #{unlocode}" unless FORMAT.match?(unlocode.to_s)
        raise ArgumentError, "場所名称は必須です" if name.nil? || name.to_s.strip.empty?

        super
      end

      # 同一地点か（UN/LOCODE で判定）。
      def same_as?(other)
        other.is_a?(Location) && other.unlocode == unlocode
      end

      def to_s = unlocode
    end
  end
end
