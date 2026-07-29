# frozen_string_literal: true

module Billing
  module Domain
    # 支払状態の不正遷移を表すドメイン例外（US23）。
    class InvalidPaymentTransitionError < StandardError; end

    # 請求書の集約ルート（US21 料金確定・US23 精算）。
    # 配送完了（DELIVERED）予約の確定料金から発行し、入金確認・期限超過を管理する。
    # Booking/Shipper への参照は越境識別子（string/id・ADR-0003）で保持する。
    class Invoice
      PAYMENT_TERM_DAYS = 30 # 支払期限は発行日 + 30 日

      attr_reader :invoice_number, :booking_id, :shipper_id, :amounts,
                  :payment_status, :issued_at, :due_date, :paid_at

      # 請求書を発行する（PENDING で採番）。支払期限は発行日 + 30 日。
      def self.generate(invoice_number:, booking_id:, shipper_id:, amounts:, issued_at:)
        new(invoice_number: invoice_number, booking_id: booking_id, shipper_id: shipper_id,
            amounts: amounts, payment_status: PaymentStatus.initial, issued_at: issued_at, paid_at: nil)
      end

      # 永続化からの復元専用。
      def self.reconstitute(**attributes)
        new(**attributes)
      end

      def initialize(invoice_number:, booking_id:, shipper_id:, amounts:, payment_status:, issued_at:, paid_at: nil)
        raise ArgumentError, "請求番号は必須です" if invoice_number.to_s.strip.empty?
        raise ArgumentError, "予約番号は必須です" if booking_id.to_s.strip.empty?

        @invoice_number = invoice_number
        @booking_id = booking_id
        @shipper_id = shipper_id
        @amounts = amounts
        @payment_status = payment_status
        @issued_at = issued_at
        @due_date = issued_at.to_date + PAYMENT_TERM_DAYS
        @paid_at = paid_at
      end

      # 金額の委譲アクセサ（明細表示・永続化で利用）。
      def base_amount = amounts.base
      def discount_rate = amounts.discount_rate
      def surcharge_amount = amounts.surcharge
      def tax_amount = amounts.tax
      def total_amount = amounts.total

      # 入金を確認して CONFIRMED に遷移する（US23）。PENDING 以外は不正遷移。
      def confirm_payment(paid_at:)
        unless payment_status.pending?
          raise InvalidPaymentTransitionError, "入金確認は PENDING の請求書のみ可能です（現在: #{payment_status}）"
        end

        @payment_status = PaymentStatus.new(value: PaymentStatus::CONFIRMED)
        @paid_at = paid_at
      end

      # 支払期限を超過していれば OVERDUE に遷移する（US23 未払い通知の起点）。
      def mark_overdue_if_due(as_of:)
        return unless payment_status.pending?
        return unless as_of.to_date > due_date

        @payment_status = PaymentStatus.new(value: PaymentStatus::OVERDUE)
      end
    end
  end
end
