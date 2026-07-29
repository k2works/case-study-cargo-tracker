# frozen_string_literal: true

module Billing
  module Domain
    # 請求明細（InvoiceLineItem）。料金調整（減額・補償費用）を表す不変値オブジェクト（US21-6）。
    # amount はマイナス（減額）・プラス（補償）の両方を取りうる。
    class InvoiceLineItem
      REDUCTION = "REDUCTION"       # 減額（当社都合の値引き・goodwill）
      COMPENSATION = "COMPENSATION" # 補償費用（遅延・破損への当社負担クレジット）

      ADJUSTMENT_TYPES = [ REDUCTION, COMPENSATION ].freeze

      attr_reader :description, :amount, :adjustment_type, :adjusted_by, :reason

      # amount は絶対値でも符号付きでも受け取り、種別に応じて符号を正規化する。
      # 符号規約をドメインに閉じる（単一の真実点）。adjusted_by/reason は監査証跡（T47b）。
      def initialize(description:, amount:, adjustment_type:, adjusted_by: nil, reason: nil)
        raise ArgumentError, "説明は必須です" if description.to_s.strip.empty?
        raise ArgumentError, "調整種別が不正です: #{adjustment_type}" unless ADJUSTMENT_TYPES.include?(adjustment_type)

        @description = description
        @adjustment_type = adjustment_type
        @amount = normalize_sign(amount, adjustment_type)
        @adjusted_by = adjusted_by
        @reason = reason
        freeze
      end

      private

      # 符号を正規化する。減額・補償費用とも請求額を減算するため負値に統一する（T45・当社負担）。
      def normalize_sign(amount, _adjustment_type)
        MoneyAmount.new(amount: -amount.amount.abs, currency: amount.currency)
      end
    end
  end
end
