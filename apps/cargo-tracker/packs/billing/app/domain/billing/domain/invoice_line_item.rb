# frozen_string_literal: true

module Billing
  module Domain
    # 請求明細（InvoiceLineItem）。料金調整（減額・補償費用）を表す不変値オブジェクト（US21-6）。
    # amount はマイナス（減額）・プラス（補償）の両方を取りうる。
    class InvoiceLineItem
      REDUCTION = "REDUCTION"       # 減額（例外による値引き）
      COMPENSATION = "COMPENSATION" # 補償費用（追加請求）

      ADJUSTMENT_TYPES = [ REDUCTION, COMPENSATION ].freeze

      attr_reader :description, :amount, :adjustment_type

      def initialize(description:, amount:, adjustment_type:)
        raise ArgumentError, "説明は必須です" if description.to_s.strip.empty?
        raise ArgumentError, "調整種別が不正です: #{adjustment_type}" unless ADJUSTMENT_TYPES.include?(adjustment_type)

        @description = description
        @amount = amount
        @adjustment_type = adjustment_type
        freeze
      end
    end
  end
end
