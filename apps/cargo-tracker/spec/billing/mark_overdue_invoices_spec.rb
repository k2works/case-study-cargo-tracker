# frozen_string_literal: true

require "rails_helper"

# US23-5: 支払期限超過の未払い通知（OVERDUE 駆動バッチ）
RSpec.describe "未払い通知の駆動（US23-5）" do
  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }
  let(:repository) { Billing::Infrastructure::ActiveRecordInvoiceRepository.new }

  def seed_invoice(number:, issued_at:, status: "PENDING")
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.none,
      surcharge: money.call(0), tax: money.call(10_000), total: money.call(110_000)
    )
    invoice = Billing::Domain::Invoice.generate(
      invoice_number: number, booking_id: "BKG-#{number}", shipper_id: 1, amounts: amounts, issued_at: issued_at
    )
    invoice.confirm_payment(paid_at: issued_at + 1.day) if status == "CONFIRMED"
    repository.save(invoice)
  end

  before do
    DomainEvents.reset!
    Booking::Public::NotificationWiring.install!
  end

  after { DomainEvents.reset! }

  it "支払期限を超過した PENDING 請求書を OVERDUE にし経理へ未払い通知する" do
    seed_invoice(number: "OVD0001", issued_at: Time.utc(2026, 9, 1)) # 期限 10/1
    seed_invoice(number: "PND0001", issued_at: Time.utc(2026, 11, 1)) # 期限 12/1（未超過）

    result = Billing::Public::BillingService.new.mark_overdue(as_of: Time.utc(2026, 10, 15))

    expect(result.overdue_count).to eq(1)
    expect(repository.find_by_invoice_number("OVD0001").payment_status.value).to eq("OVERDUE")
    expect(repository.find_by_invoice_number("PND0001").payment_status.value).to eq("PENDING")

    notifications = Shared::Public::NotificationRecorder.new.for(notifiable_type: "Cargo", notifiable_id: "BKG-OVD0001")
    expect(notifications.map(&:event_type)).to include("INVOICE_OVERDUE")
    expect(notifications.map(&:recipient_type)).to include("ACCOUNTANT")
  end

  it "CONFIRMED（精算済）は期限超過でも OVERDUE にしない（負の同値）" do
    seed_invoice(number: "CNF0001", issued_at: Time.utc(2026, 9, 1), status: "CONFIRMED")
    result = Billing::Public::BillingService.new.mark_overdue(as_of: Time.utc(2026, 10, 15))

    expect(result.overdue_count).to eq(0)
    expect(repository.find_by_invoice_number("CNF0001").payment_status.value).to eq("CONFIRMED")
  end

  it "多重実行しても既 OVERDUE は二重通知しない（冪等・通知件数で検証）" do
    seed_invoice(number: "OVD0002", issued_at: Time.utc(2026, 9, 1)) # 期限 10/1・BKG-OVD0002
    service = Billing::Public::BillingService.new
    service.mark_overdue(as_of: Time.utc(2026, 10, 15))
    second = service.mark_overdue(as_of: Time.utc(2026, 10, 16))

    expect(second.overdue_count).to eq(0)
    # 通知は 1 件のまま（二重通知しない）
    notifications = Shared::Public::NotificationRecorder.new.for(notifiable_type: "Cargo", notifiable_id: "BKG-OVD0002")
    expect(notifications.count { |n| n.event_type == "INVOICE_OVERDUE" }).to eq(1)
  end

  it "支払期限ちょうどは OVERDUE にしない・翌日は OVERDUE にする（境界値）" do
    seed_invoice(number: "BND0001", issued_at: Time.utc(2026, 9, 1)) # 期限 10/1
    service = Billing::Public::BillingService.new
    service.mark_overdue(as_of: Time.utc(2026, 10, 1, 23, 59)) # 期限当日
    expect(repository.find_by_invoice_number("BND0001").payment_status.value).to eq("PENDING")
    service.mark_overdue(as_of: Time.utc(2026, 10, 2)) # 期限翌日
    expect(repository.find_by_invoice_number("BND0001").payment_status.value).to eq("OVERDUE")
  end

  it "複数件の期限超過を全件 OVERDUE 化する" do
    seed_invoice(number: "MUL0001", issued_at: Time.utc(2026, 9, 1))
    seed_invoice(number: "MUL0002", issued_at: Time.utc(2026, 8, 1))
    result = Billing::Public::BillingService.new.mark_overdue(as_of: Time.utc(2026, 10, 15))
    expect(result.overdue_count).to eq(2)
  end
end
