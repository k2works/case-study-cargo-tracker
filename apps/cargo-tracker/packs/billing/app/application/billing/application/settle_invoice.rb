# frozen_string_literal: true

module Billing
  module Application
    # 精算処理ユースケース（US23）。決済機関で入金を確認し、請求書を CONFIRMED に遷移させ、
    # 予約を SETTLED に同期する。invoice_settled を発行し荷主へ精算完了を通知する（ADR-0002）。
    class SettleInvoice
      Result = Struct.new(:status, :invoice_number, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new,
                     payment_gateway: Infrastructure::StubPaymentGateway.new,
                     booking_service: Booking::Public::CargoBookingService.new,
                     clock: -> { Time.current })
        @repository = repository
        @payment_gateway = payment_gateway
        @booking_service = booking_service
        @clock = clock
      end

      def call(invoice_number)
        invoice = @repository.find_by_invoice_number(invoice_number)
        return Result.new(status: :not_found) if invoice.nil?

        payment = @payment_gateway.confirm_payment(
          invoice_number: invoice.invoice_number, amount: invoice.total_amount.amount.to_i
        )
        return Result.new(status: :payment_failed, error_message: "入金確認に失敗しました") unless payment.confirmed?

        invoice.confirm_payment(paid_at: @clock.call)
        @repository.save(invoice)
        @booking_service.mark_settled(invoice.booking_id)

        DomainEvents.publish("invoice_settled", {
          booking_id: invoice.booking_id, invoice_number: invoice.invoice_number,
          total_amount: invoice.total_amount.amount.to_i
        })
        Result.new(status: :ok, invoice_number: invoice.invoice_number)
      rescue Domain::InvalidPaymentTransitionError => e
        Result.new(status: :invalid, error_message: e.message)
      end
    end
  end
end
