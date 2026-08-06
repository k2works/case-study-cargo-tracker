# frozen_string_literal: true

require "rails_helper"

# IT7: 請求書集約のドメイン層（US21 確定・US23 精算）
RSpec.describe "Billing Context 請求書集約" do
  describe Billing::Domain::PaymentStatus do
    it "PENDING/CONFIRMED/OVERDUE/REFUNDED を許可する" do
      %w[PENDING CONFIRMED OVERDUE REFUNDED].each { |v| expect(described_class.new(value: v).value).to eq(v) }
    end

    it "未知の状態は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end

    it "初期状態は PENDING" do
      expect(described_class.initial.value).to eq("PENDING")
    end
  end

  describe Billing::Domain::Invoice do
    let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

    def build_invoice(discount: "0.0")
      amounts = Billing::Domain::InvoiceAmounts.new(
        base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.new(rate: BigDecimal(discount)),
        surcharge: money.call(0), tax: money.call(9_000), total: money.call(99_000)
      )
      Billing::Domain::Invoice.generate(
        invoice_number: "INV-0001", booking_id: "BKG-ABCD1234", shipper_id: 1,
        amounts: amounts, issued_at: Time.utc(2026, 11, 1)
      )
    end

    it "請求書を発行すると PENDING 状態で採番される（US23）" do
      invoice = build_invoice(discount: "0.10")
      expect(invoice.invoice_number).to eq("INV-0001")
      expect(invoice.payment_status.value).to eq("PENDING")
      expect(invoice.total_amount.amount).to eq(99_000)
    end

    it "支払期限は発行日 + 30 日" do
      invoice = build_invoice
      expect(invoice.due_date).to eq(Date.new(2026, 12, 1))
    end

    it "入金確認で CONFIRMED に遷移する（US23）" do
      invoice = build_invoice
      invoice.confirm_payment(paid_at: Time.utc(2026, 11, 10))
      expect(invoice.payment_status.value).to eq("CONFIRMED")
      expect(invoice.paid_at).to eq(Time.utc(2026, 11, 10))
    end

    it "PENDING 以外での入金確認は拒否する" do
      invoice = build_invoice
      invoice.confirm_payment(paid_at: Time.utc(2026, 11, 10))
      expect { invoice.confirm_payment(paid_at: Time.utc(2026, 11, 11)) }
        .to raise_error(Billing::Domain::InvalidPaymentTransitionError)
    end

    it "支払期限超過を判定して OVERDUE に遷移できる（US23 未払い）" do
      invoice = build_invoice
      # 期限（12/1）を過ぎた時点
      invoice.mark_overdue_if_due(as_of: Time.utc(2026, 12, 2))
      expect(invoice.payment_status.value).to eq("OVERDUE")
    end

    it "期限内は OVERDUE にならない" do
      invoice = build_invoice
      invoice.mark_overdue_if_due(as_of: Time.utc(2026, 11, 15))
      expect(invoice.payment_status.value).to eq("PENDING")
    end
  end
end
