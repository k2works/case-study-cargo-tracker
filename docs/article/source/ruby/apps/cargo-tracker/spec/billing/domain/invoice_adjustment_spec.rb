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

    it "補償費用を追加すると請求金額が減る（当社負担・US21-6・T45）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "破損補償費用", amount: money.call(5_000), adjustment_type: "COMPENSATION"
      ))
      # 補償費用は当社が荷主へ負担するクレジットのため請求額を減算する（110,000 - 5,000）
      expect(invoice.total_amount.amount).to eq(105_000)
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

    it "期限超過（OVERDUE）の請求書は料金調整できる（レビュー高・塩漬け解消）" do
      invoice = build_invoice
      invoice.mark_overdue_if_due(as_of: invoice.due_date + 1)
      expect(invoice.payment_status.value).to eq("OVERDUE")
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "遅延減額", amount: money.call(10_000), adjustment_type: "REDUCTION"
      ))
      expect(invoice.total_amount.amount).to eq(100_000) # 110,000 - 10,000
    end

    it "過大な減額で請求金額が 0 を下回る調整は拒否する（下限ガード）" do
      invoice = build_invoice
      expect {
        invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
          description: "過大減額", amount: money.call(200_000), adjustment_type: "REDUCTION"
        ))
      }.to raise_error(ArgumentError, /0 を下回/)
    end

    it "絶対値入力は種別によらず減算方向（負値）に正規化される（REDUCTION・COMPENSATION とも・ADR-0005）" do
      # 正値を渡しても、減額・補償費用のいずれも請求額を減らす負値として計上される（符号はドメインに閉じる）
      reduction = Billing::Domain::InvoiceLineItem.new(description: "減額", amount: money.call(5_000), adjustment_type: "REDUCTION")
      compensation = Billing::Domain::InvoiceLineItem.new(description: "補償", amount: money.call(5_000), adjustment_type: "COMPENSATION")
      expect(reduction.amount.amount).to eq(-5_000)
      expect(compensation.amount.amount).to eq(-5_000)
    end

    it "調整を取り消すと明細が除去され請求金額が戻る（US21-6・T47a）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "減額", amount: money.call(10_000), adjustment_type: "REDUCTION"
      ))
      invoice.remove_adjustment(1)
      expect(invoice.line_items).to be_empty
      expect(invoice.total_amount.amount).to eq(110_000)
    end

    it "OVERDUE の請求書の調整も取り消せる（未精算の対称性・レビュー高）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(
        description: "減額", amount: money.call(10_000), adjustment_type: "REDUCTION"
      ))
      invoice.mark_overdue_if_due(as_of: invoice.due_date + 1)
      invoice.remove_adjustment(1)
      expect(invoice.total_amount.amount).to eq(110_000)
    end

    it "複数調整のうち中間の1件を取り消すと残明細と合計が正しい（seq は位置ベース）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(description: "減額A", amount: money.call(10_000), adjustment_type: "REDUCTION"))
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(description: "減額B", amount: money.call(20_000), adjustment_type: "REDUCTION"))
      invoice.remove_adjustment(1) # A を取消
      expect(invoice.line_items.map(&:description)).to eq([ "減額B" ])
      expect(invoice.total_amount.amount).to eq(90_000) # 110,000 - 20,000
    end

    it "seq_number の 0・負値・範囲外は末尾誤削除を避けて拒否する（境界値ガード）" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(description: "減額", amount: money.call(10_000), adjustment_type: "REDUCTION"))
      expect { invoice.remove_adjustment(0) }.to raise_error(ArgumentError)
      expect { invoice.remove_adjustment(-1) }.to raise_error(ArgumentError)
      expect { invoice.remove_adjustment(2) }.to raise_error(ArgumentError)
      expect(invoice.line_items.size).to eq(1) # 何も消えていない
    end

    it "精算済（CONFIRMED）の請求書の調整は取り消せない" do
      invoice = build_invoice
      invoice.add_adjustment(Billing::Domain::InvoiceLineItem.new(description: "減額", amount: money.call(10_000), adjustment_type: "REDUCTION"))
      invoice.confirm_payment(paid_at: Time.utc(2026, 11, 10))
      expect { invoice.remove_adjustment(1) }.to raise_error(Billing::Domain::InvalidPaymentTransitionError)
    end
  end

  describe "#{Billing::Domain::Invoice} の期限超過後の入金確認" do
    it "OVERDUE の請求書も入金確認で CONFIRMED にできる（遅延入金・レビュー高）" do
      invoice = build_invoice
      invoice.mark_overdue_if_due(as_of: invoice.due_date + 1)
      invoice.confirm_payment(paid_at: Time.utc(2026, 12, 5))
      expect(invoice.payment_status.value).to eq("CONFIRMED")
    end
  end
end
