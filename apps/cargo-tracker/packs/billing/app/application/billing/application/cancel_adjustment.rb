# frozen_string_literal: true

module Billing
  module Application
    # 料金調整の取消ユースケース（US21-6・T47a）。誤入力の是正。
    # 未精算の請求書の調整明細を取り消し、請求金額を再計算して保存する。
    class CancelAdjustment
      Result = Struct.new(:status, :invoice_number, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new)
        @repository = repository
      end

      def call(invoice_number:, seq_number:)
        invoice = @repository.find_by_invoice_number(invoice_number)
        return Result.new(status: :not_found) if invoice.nil?

        invoice.remove_adjustment(seq_number)
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
