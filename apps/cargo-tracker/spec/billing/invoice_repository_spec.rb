# frozen_string_literal: true

require "rails_helper"

# IT7: 請求書リポジトリの永続化（US21/US23）。集約（PORO）と AR の相互変換を検証する。
RSpec.describe Billing::Infrastructure::ActiveRecordInvoiceRepository do
  subject(:repository) { described_class.new }

  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

  def build_invoice(number: "INV-0001", booking: "BKG-ABCD1234")
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.new(rate: BigDecimal("0.10")),
      surcharge: money.call(0), tax: money.call(9_000), total: money.call(99_000)
    )
    Billing::Domain::Invoice.generate(
      invoice_number: number, booking_id: booking, shipper_id: 1, amounts: amounts, issued_at: Time.utc(2026, 11, 1)
    )
  end

  it "請求書を保存して請求番号で取得できる" do
    repository.save(build_invoice)
    found = repository.find_by_invoice_number("INV-0001")
    expect(found).not_to be_nil
    expect(found.booking_id).to eq("BKG-ABCD1234")
    expect(found.total_amount.amount).to eq(99_000)
    expect(found.payment_status.value).to eq("PENDING")
    expect(found.due_date).to eq(Date.new(2026, 12, 1))
  end

  it "予約番号で取得できる（二重請求チェック）" do
    repository.save(build_invoice)
    expect(repository.find_by_booking_id("BKG-ABCD1234")).not_to be_nil
    expect(repository.exists_for_booking?("BKG-ABCD1234")).to be true
    expect(repository.exists_for_booking?("BKG-OTHER")).to be false
  end

  it "入金確認の状態遷移を永続化できる" do
    repository.save(build_invoice)
    invoice = repository.find_by_invoice_number("INV-0001")
    invoice.confirm_payment(paid_at: Time.utc(2026, 11, 10))
    repository.save(invoice)

    reloaded = repository.find_by_invoice_number("INV-0001")
    expect(reloaded.payment_status.value).to eq("CONFIRMED")
    expect(reloaded.paid_at).to be_within(1).of(Time.utc(2026, 11, 10))
  end

  it "全請求書を取得できる" do
    repository.save(build_invoice(number: "INV-0001", booking: "BKG-1"))
    repository.save(build_invoice(number: "INV-0002", booking: "BKG-2"))
    expect(repository.all.size).to eq(2)
  end
end
