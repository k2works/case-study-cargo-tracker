# frozen_string_literal: true

module Billing
  module Infrastructure
    # 請求書リポジトリの Active Record 実装（出力アダプタ）。
    # Invoice 集約（PORO）と InvoiceRecord（AR）の相互変換を担う。
    class ActiveRecordInvoiceRepository < Domain::InvoiceRepository
      def save(invoice)
        record = InvoiceRecord.find_or_initialize_by(invoice_number: invoice.invoice_number)
        record.assign_attributes(
          booking_id: invoice.booking_id,
          total_amount_value: invoice.total_amount.amount.to_i,
          total_amount_currency: invoice.total_amount.currency,
          tax_amount: invoice.tax_amount.amount,
          discount_amount_value: discount_value(invoice),
          discount_amount_currency: invoice.total_amount.currency,
          payment_status: invoice.payment_status.value,
          issued_at: invoice.issued_at, due_date: invoice.due_date,
          paid_at: invoice.paid_at
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

      private

      # 割引額（基本 − 割引後）。ドメインの割引率から導出できないため、明細に持たせず概算保存。
      def discount_value(invoice)
        (invoice.base_amount.amount * invoice.discount_rate.rate).to_i
      end

      def to_domain(record)
        money = ->(v) { Domain::MoneyAmount.new(amount: v, currency: record.total_amount_currency) }
        base = money.call(record.total_amount_value - record.tax_amount.to_i + record.discount_amount_value.to_i)
        Domain::Invoice.reconstitute(
          invoice_number: record.invoice_number, booking_id: record.booking_id, shipper_id: nil,
          base_amount: base,
          discount_rate: Domain::DiscountRate.new(rate: discount_rate_of(record, base)),
          tax_amount: money.call(record.tax_amount), total_amount: money.call(record.total_amount_value),
          payment_status: Domain::PaymentStatus.new(value: record.payment_status),
          issued_at: record.issued_at, paid_at: record.paid_at
        )
      end

      # 保存済み割引額と基本料金から割引率を復元する。
      def discount_rate_of(record, base)
        return BigDecimal("0") if base.amount.zero? || record.discount_amount_value.to_i.zero?

        (BigDecimal(record.discount_amount_value.to_s) / base.amount).round(4)
      end
    end
  end
end
