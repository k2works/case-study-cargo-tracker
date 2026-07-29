# frozen_string_literal: true

require "rails_helper"

# US23: 精算処理のアプリ層〜永続化・BC 越境（入金確認→CONFIRMED→予約 SETTLED・通知）の結合。
RSpec.describe "精算処理（US23）" do
  subject(:billing) { Billing::Public::BillingService.new }

  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

  # PENDING の請求書を用意する。
  def seed_invoice(booking: "BKG-ABCD1234")
    repo = Billing::Infrastructure::ActiveRecordInvoiceRepository.new
    invoice = Billing::Domain::Invoice.generate(
      invoice_number: "INV-000001", booking_id: booking, shipper_id: 1,
      base_amount: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.none,
      tax_amount: money.call(10_000), total_amount: money.call(110_000), issued_at: Time.utc(2026, 11, 1)
    )
    repo.save(invoice)
    invoice.invoice_number
  end

  # 決済成功を返すゲートウェイスタブ。
  let(:gateway_ok) { Class.new { def confirm_payment(**_) = Struct.new(:confirmed) { def confirmed? = confirmed }.new(true) }.new }
  # 決済失敗を返すゲートウェイスタブ。
  let(:gateway_ng) { Class.new { def confirm_payment(**_) = Struct.new(:confirmed) { def confirmed? = confirmed }.new(false) }.new }
  # 予約の SETTLED 同期を記録するスタブ。
  let(:booking_spy) { Class.new { attr_reader :settled; def mark_settled(id) = (@settled = id) && :ok }.new }

  before do
    DomainEvents.reset!
    Booking::Public::NotificationWiring.install!
  end

  after { DomainEvents.reset! }

  it "入金確認で CONFIRMED になり予約が SETTLED に同期される（US23）" do
    number = seed_invoice
    result = billing.settle(number, payment_gateway: gateway_ok, booking_service: booking_spy)

    expect(result.status).to eq(:ok)
    expect(billing.find_invoice(number).payment_status).to eq("CONFIRMED")
    expect(booking_spy.settled).to eq("BKG-ABCD1234")
  end

  it "決済失敗時は CONFIRMED にせず :payment_failed を返す" do
    number = seed_invoice
    result = billing.settle(number, payment_gateway: gateway_ng, booking_service: booking_spy)

    expect(result.status).to eq(:payment_failed)
    expect(billing.find_invoice(number).payment_status).to eq("PENDING")
    expect(booking_spy.settled).to be_nil
  end

  it "存在しない請求書は :not_found を返す" do
    expect(billing.settle("INV-NONE", payment_gateway: gateway_ok, booking_service: booking_spy).status).to eq(:not_found)
  end
end
