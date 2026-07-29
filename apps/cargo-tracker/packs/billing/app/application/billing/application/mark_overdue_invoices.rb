# frozen_string_literal: true

module Billing
  module Application
    # 未払い通知の駆動ユースケース（US23-5）。支払期限を超過した PENDING 請求書を検出し、
    # OVERDUE に遷移させて経理担当者へ未払い通知（invoice_overdue）を発行する。
    # 定期実行（Rake タスク / cron）から呼び出す。既 OVERDUE は対象外のため冪等。
    class MarkOverdueInvoices
      Result = Struct.new(:overdue_count, keyword_init: true)

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new, clock: -> { Time.current })
        @repository = repository
        @clock = clock
      end

      def call(as_of: nil)
        reference = as_of || @clock.call
        count = 0
        @repository.pending_overdue(as_of: reference).each do |invoice|
          # 実際に OVERDUE へ遷移した時だけ保存・通知する（状態遷移に通知を紐づける・T33）。
          next unless invoice.mark_overdue_if_due(as_of: reference)

          @repository.save(invoice)
          DomainEvents.publish("invoice_overdue", {
            booking_id: invoice.booking_id, invoice_number: invoice.invoice_number,
            total_amount: invoice.total_amount.amount.to_i, due_date: invoice.due_date
          })
          count += 1
        end
        Result.new(overdue_count: count)
      end
    end
  end
end
