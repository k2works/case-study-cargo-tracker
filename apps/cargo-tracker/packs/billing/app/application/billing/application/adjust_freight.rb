# frozen_string_literal: true

module Billing
  module Application
    # 料金調整ユースケース（US21-6）。例外発生時の減額・補償費用を請求書明細に追加し、
    # 請求金額を再計算して保存する。精算済みの請求書は調整できない。
    class AdjustFreight
      Result = Struct.new(:status, :invoice_number, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new, clock: -> { Time.current })
        @repository = repository
        @clock = clock
      end

      def call(invoice_number:, description:, amount:, adjustment_type:, adjusted_by: nil, reason: nil)
        invoice = @repository.find_by_invoice_number(invoice_number)
        return Result.new(status: :not_found) if invoice.nil?

        # 符号の正規化は InvoiceLineItem 側で行う（ドメインに閉じる）。監査証跡（担当者・理由・日時）を付与する。
        item = Domain::InvoiceLineItem.new(
          description: description,
          amount: Domain::MoneyAmount.new(amount: amount.to_i, currency: invoice.total_amount.currency),
          adjustment_type: adjustment_type, adjusted_by: adjusted_by, reason: reason, adjusted_at: @clock.call
        )
        invoice.add_adjustment(item)
        @repository.save(invoice)
        Result.new(status: :ok, invoice_number: invoice.invoice_number)
      rescue Domain::InvalidPaymentTransitionError => e
        Result.new(status: :invalid, error_message: e.message)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end
    end
  end
end
