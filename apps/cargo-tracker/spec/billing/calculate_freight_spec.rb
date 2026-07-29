# frozen_string_literal: true

require "rails_helper"

# US21/US22: 輸送料金算出・法人割引のアプリ層〜永続化・BC 越境（Booking 実績・Shipper 割引）の結合。
RSpec.describe "輸送料金算出（US21/US22）" do
  subject(:billing) { Billing::Public::BillingService.new }

  # DELIVERED 予約ビューのスタブ（Booking 公開 API の代替）。
  def booking_stub(status: "DELIVERED", cargo_type: "GENERAL", shipper_id: 1)
    view = Struct.new(:booking_id, :shipper_id, :cargo_type, :weight_kg, :status_value, :itinerary_legs,
                      keyword_init: true) do
      def delivered? = status_value == "DELIVERED"
    end.new(booking_id: "BKG-ABCD1234", shipper_id: shipper_id, cargo_type: cargo_type,
            weight_kg: BigDecimal("1000"), status_value: status,
            itinerary_legs: [ Struct.new(:load_location, :unload_location).new("JPTYO", "USLAX") ])
    Class.new { def initialize(v) = (@v = v); def find(_id) = @v }.new(view)
  end

  # 荷主ビューのスタブ（Shipper 公開 API の代替）。
  def shipper_stub(corporate:, discount_percentage:)
    view = Struct.new(:corporate, :discount_percentage, keyword_init: true) do
      def corporate? = corporate
    end.new(corporate: corporate, discount_percentage: discount_percentage)
    Class.new { def initialize(v) = (@v = v); def find(_id) = @v }.new(view)
  end

  it "DELIVERED 予約に法人割引を適用して請求書を生成する（US21/US22）" do
    result = billing.calculate_freight(
      "BKG-ABCD1234",
      booking_service: booking_stub, shipper_directory: shipper_stub(corporate: true, discount_percentage: 10)
    )
    expect(result.status).to eq(:ok)

    invoice = billing.find_invoice(result.invoice_number)
    expect(invoice.payment_status).to eq("PENDING")
    expect(invoice.discount_percentage).to eq(10)
    expect(invoice.total_amount).to be > 0
    # 割引後 < 基本料金（法人割引が効いている）
    expect(invoice.discount_amount).to be > 0
  end

  it "個人荷主は割引が適用されない（US22）" do
    result = billing.calculate_freight(
      "BKG-ABCD1234",
      booking_service: booking_stub, shipper_directory: shipper_stub(corporate: false, discount_percentage: nil)
    )
    invoice = billing.find_invoice(result.invoice_number)
    expect(invoice.discount_amount).to eq(0)
  end

  it "請求書発行時に荷主へ精算書発行通知が送られる（US23・T34）" do
    DomainEvents.reset!
    Booking::Public::NotificationWiring.install!
    result = billing.calculate_freight("BKG-ABCD1234", booking_service: booking_stub,
                                       shipper_directory: shipper_stub(corporate: false, discount_percentage: nil))
    notifications = Shared::Public::NotificationRecorder.new.for(notifiable_type: "Cargo", notifiable_id: "BKG-ABCD1234")
    expect(notifications.map(&:event_type)).to include("INVOICE_CREATED")
  ensure
    DomainEvents.reset!
  end

  it "DELIVERED でない予約は料金算出できない（:invalid）" do
    result = billing.calculate_freight(
      "BKG-ABCD1234",
      booking_service: booking_stub(status: "IN_TRANSIT"), shipper_directory: shipper_stub(corporate: false, discount_percentage: nil)
    )
    expect(result.status).to eq(:invalid)
  end

  it "既に請求済みの予約は二重請求しない（:already_invoiced）" do
    billing.calculate_freight("BKG-ABCD1234", booking_service: booking_stub,
                              shipper_directory: shipper_stub(corporate: false, discount_percentage: nil))
    again = billing.calculate_freight("BKG-ABCD1234", booking_service: booking_stub,
                                      shipper_directory: shipper_stub(corporate: false, discount_percentage: nil))
    expect(again.status).to eq(:already_invoiced)
  end
end
