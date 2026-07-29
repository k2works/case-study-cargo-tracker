# frozen_string_literal: true

require "rails_helper"

# US21-6/T47: 料金調整の取消・監査証跡（担当者・理由）
RSpec.describe "料金調整のライフサイクル（取消・監査・T47）" do
  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }
  let(:repository) { Billing::Infrastructure::ActiveRecordInvoiceRepository.new }

  def seed_invoice
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.none,
      surcharge: money.call(0), tax: money.call(10_000), total: money.call(110_000)
    )
    repository.save(Billing::Domain::Invoice.generate(
      invoice_number: "INV-T47", booking_id: "BKG-T47", shipper_id: 1, amounts: amounts, issued_at: Time.utc(2026, 11, 1)
    ))
    "INV-T47"
  end

  describe Billing::Domain::InvoiceLineItem do
    it "担当者・理由を保持する（監査証跡・T47b）" do
      item = described_class.new(description: "遅延減額", amount: money.call(5_000), adjustment_type: "REDUCTION",
                                 adjusted_by: "経理太郎", reason: "台風遅延の補填")
      expect(item.adjusted_by).to eq("経理太郎")
      expect(item.reason).to eq("台風遅延の補填")
    end
  end

  describe "取消・監査（BillingService）" do
    subject(:billing) { Billing::Public::BillingService.new }

    it "料金調整に担当者・理由が記録され請求書詳細で確認できる（T47b）" do
      number = seed_invoice
      billing.adjust(number, description: "遅延減額", amount: 5_000, adjustment_type: "REDUCTION",
                     adjusted_by: "経理太郎", reason: "台風遅延")
      view = billing.find_invoice(number)
      li = view.line_items.first
      expect(li.adjusted_by).to eq("経理太郎")
      expect(li.reason).to eq("台風遅延")
      # 監査日時が保存・復元される（担当者・理由・日時の 3 点・T47b）。リポジトリ往復後も保持。
      expect(li.adjusted_at).to be_present
      expect(view.total_amount).to eq(105_000)
    end

    it "料金調整を取り消すと請求金額が調整前に戻る（T47a）" do
      number = seed_invoice
      billing.adjust(number, description: "誤入力減額", amount: 20_000, adjustment_type: "REDUCTION",
                     adjusted_by: "経理太郎", reason: "誤入力")
      expect(billing.find_invoice(number).total_amount).to eq(90_000)

      result = billing.cancel_adjustment(number, seq_number: 1)
      expect(result.status).to eq(:ok)
      expect(billing.find_invoice(number).total_amount).to eq(110_000)
      expect(billing.find_invoice(number).line_items).to be_empty
    end

    it "精算済の請求書の調整は取り消せない" do
      number = seed_invoice
      billing.adjust(number, description: "減額", amount: 5_000, adjustment_type: "REDUCTION",
                     adjusted_by: "経理", reason: "x")
      # 精算済にする
      inv = repository.find_by_invoice_number(number)
      inv.confirm_payment(paid_at: Time.utc(2026, 11, 10))
      repository.save(inv)
      expect(billing.cancel_adjustment(number, seq_number: 1).status).to eq(:invalid)
    end
  end
end
