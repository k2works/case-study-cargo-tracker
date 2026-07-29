# frozen_string_literal: true

module Billing
  module Infrastructure
    # 請求書リポジトリの Active Record 実装（出力アダプタ）。
    # Invoice 集約（PORO）と InvoiceRecord（AR）の相互変換を担う。
    class ActiveRecordInvoiceRepository < Domain::InvoiceRepository
      def save(invoice)
        record = InvoiceRecord.find_or_initialize_by(invoice_number: invoice.invoice_number)
        record.assign_attributes(
          booking_id: invoice.booking_id, shipper_id: invoice.shipper_id,
          base_amount_value: invoice.base_amount.amount.to_i,
          total_amount_value: invoice.total_amount.amount.to_i,
          total_amount_currency: invoice.total_amount.currency,
          tax_amount: invoice.tax_amount.amount,
          surcharge_amount_value: invoice.surcharge_amount.amount.to_i,
          discount_amount_value: discount_value(invoice),
          discount_amount_currency: invoice.total_amount.currency,
          payment_status: invoice.payment_status.value,
          issued_at: invoice.issued_at, due_date: invoice.due_date, paid_at: invoice.paid_at
        )
        record.save!
        invoice
      end

      def find_by_invoice_number(invoice_number)
        record = InvoiceRecord.find_by(invoice_number: invoice_number)
        record && to_domain(record)
      end

      def find_by_booking_id(booking_id)
        record = InvoiceRecord.find_by(booking_id: booking_id)
        record && to_domain(record)
      end

      def exists_for_booking?(booking_id)
        InvoiceRecord.exists?(booking_id: booking_id)
      end

      def all
        InvoiceRecord.order(created_at: :desc).map { |r| to_domain(r) }
      end

      # 支払期限を超過した PENDING 請求書を返す（US23-5 未払い通知の検出）。
      def pending_overdue(as_of:)
        InvoiceRecord.where(payment_status: "PENDING").where(due_date: ...as_of.to_date).map { |r| to_domain(r) }
      end

      private

      # 割引額（基本料金 × 割引率）。永続値として保存し復元時の逆算を避ける。
      def discount_value(invoice)
        (invoice.base_amount.amount * invoice.discount_rate.rate).to_i
      end

      def to_domain(record)
        money = ->(v) { Domain::MoneyAmount.new(amount: v, currency: record.total_amount_currency) }
        amounts = Domain::InvoiceAmounts.new(
          base: money.call(record.base_amount_value.to_i),
          discount_rate: Domain::DiscountRate.new(rate: discount_rate_of(record)),
          surcharge: money.call(record.surcharge_amount_value.to_i),
          tax: money.call(record.tax_amount), total: money.call(record.total_amount_value)
        )
        Domain::Invoice.reconstitute(
          invoice_number: record.invoice_number, booking_id: record.booking_id, shipper_id: record.shipper_id,
          amounts: amounts, payment_status: Domain::PaymentStatus.new(value: record.payment_status),
          issued_at: record.issued_at, paid_at: record.paid_at
        )
      end

      # 保存済みの基本料金と割引額から割引率を厳密に復元する（永続値からの算出）。
      def discount_rate_of(record)
        base = record.base_amount_value.to_i
        return BigDecimal("0") if base.zero? || record.discount_amount_value.to_i.zero?

        (BigDecimal(record.discount_amount_value.to_s) / base).round(4)
      end
    end
  end
end
