# frozen_string_literal: true

require "rails_helper"

# US21-6: 例外発生時の料金調整（減額・補償費用の明細）
RSpec.describe "請求書の料金調整（US21-6）" do
  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

  def build_invoice
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.none,
      surcharge: money.call(0), tax: money.call(10_000), total: money.call(110_000)
    )
    Billing::Domain::Invoice.generate(
      invoice_number: "INV-ADJ", booking_id: "BKG-ADJ", shipper_id: 1, amounts: amounts, issued_at: Time.utc(2026, 11, 1)
    )
  end

  describe Billing::Domain::InvoiceLineItem do
    it "調整明細（種別・金額・説明）を保持する" do
      item = described_class.new(description: "遅延補償", amount: money.call(-5_000), adjustment_type: "REDUCTION")
      expect(item.description).to eq("遅延補償")
      expect(item.amount.amount).to eq(-5_000)
      expect(item.adjustment_type).to eq("REDUCTION")
    end

    it "未知の調整種別は拒否する" do
      expect { described_class.new(description: "x", amount: money.call(1), adjustment_type: "UNKNOWN") }
        .to raise_error(ArgumentError)
    end
  end

  describe "#{Billing::Domain::Invoice} の料金調整" do
    it "減額を追加すると請求金額が減る（US21-6）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "遅延による減額", amount: money.call(-11_000), adjustment_type: "REDUCTION"
      ))
      expect(invoice.total_amount.amount).to eq(99_000) # 110,000 - 11,000
      expect(invoice.line_items.size).to eq(1)
    end

    it "補償費用を追加すると請求金額が増える（US21-6）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "破損補償費用", amount: money.call(5_000), adjustment_type: "COMPENSATION"
      ))
      expect(invoice.total_amount.amount).to eq(115_000)
    end

    it "精算済（CONFIRMED）の請求書には調整できない" do
      invoice = build_invoice
      invoice.confirm_payment(paid_at: Time.utc(2026, 11, 10))
      expect {
        invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
          description: "x", amount: money.call(-1), adjustment_type: "REDUCTION"
        ))
      }.to raise_error(Billing::Domain::InvalidPaymentTransitionError)
    end
  end
end
