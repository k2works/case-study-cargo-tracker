# frozen_string_literal: true

require "rails_helper"

# US21/US22/US23: 請求・精算の HTTP フロー（経理担当者ロール）。
RSpec.describe "請求・精算（US21/US22/US23）", type: :request do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  before { stub_request(:post, %r{/search}).to_timeout }

  # 法人荷主 + DELIVERED 予約を用意し予約番号を返す。
  def delivered_booking
    shipper_id = ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, discount_rate, created_at, updated_at) " \
      "VALUES ('SHP-BINV', 'CORPORATE', '法人荷主', 'binv@example.com', 0.10, NOW(), NOW())"
    )
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    booking_service.assign_itinerary(result.booking_id, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) }
    ])
    booking_service.confirm(result.booking_id)
    tn = Tracking::Public::TrackingService.new.issue_tracking_number(result.booking_id).tracking_number
    handling = Handling::Public::HandlingService.new
    handling.register(tracking_number: tn, event_type: "LOAD", location: "JPTYO",
                      completion_time: Time.utc(2026, 9, 2, 10), voyage_number: "V001", operator_name: "作業員")
    handling.register(tracking_number: tn, event_type: "CLAIM", location: "USLAX",
                      completion_time: Time.utc(2026, 11, 21, 10), operator_name: "作業員",
                      recipient: { name: "受取人", confirmation_code: "OK123" })
    result.booking_id
  end

  def sign_in_billing
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "billing")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

  # PENDING の請求書を直接シードする（法人割引 10%）。
  def seed_invoice
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.new(rate: BigDecimal("0.10")),
      surcharge: money.call(0), tax: money.call(9_000), total: money.call(99_000)
    )
    Billing::Infrastructure::ActiveRecordInvoiceRepository.new.save(
      Billing::Domain::Invoice.generate(
        invoice_number: "INV-000001", booking_id: "BKG-ABCD1234", shipper_id: 1,
        amounts: amounts, issued_at: Time.utc(2026, 11, 1)
      )
    )
    "INV-000001"
  end

  it "請求書一覧を表示できる" do
    sign_in_billing
    seed_invoice
    get billing_invoices_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("請求書一覧")
    expect(response.body).to include("INV-000001")
  end

  it "請求書詳細で料金明細と割引根拠を表示する（US22）" do
    sign_in_billing
    number = seed_invoice
    get billing_invoice_path(number)
    expect(response.body).to include("基本料金")
    expect(response.body).to include("割引")     # 割引根拠
    expect(response.body).to include("消費税")
    expect(response.body).to include("99,000")   # 合計金額
  end

  it "入金確認で精算済（CONFIRMED）になり予約も SETTLED になる（US23）" do
    sign_in_billing
    booking_id = delivered_booking
    number = Billing::Public::BillingService.new.calculate_freight(booking_id).invoice_number
    post confirm_billing_invoice_path(number)
    follow_redirect!
    expect(response.body).to include("入金を確認しました")
    expect(Billing::Public::BillingService.new.find_invoice(number).payment_status).to eq("CONFIRMED")
    expect(booking_service.find(booking_id).status_value).to eq("SETTLED")
  end

  it "DELIVERED でない予約の料金算出はエラーを返す（US21）" do
    sign_in_billing
    post billing_invoices_path, params: { booking_id: "BKG-NONEXIST" }
    follow_redirect!
    expect(response.body).to include("料金算出できません").or include("見つかりません")
  end

  it "料金調整（減額）を追加すると明細に記録され請求金額が減る（US21-6）" do
    sign_in_billing
    number = seed_invoice # 合計 99,000
    post adjust_billing_invoice_path(number),
         params: { adjustment_type: "REDUCTION", description: "遅延による減額", amount: "9000" }
    follow_redirect!
    expect(response.body).to include("料金調整を追加しました")
    expect(response.body).to include("減額")
    expect(Billing::Public::BillingService.new.find_invoice(number).total_amount).to eq(90_000)
  end

  it "補償費用を追加すると請求金額が減る（当社負担・US21-6・T45）" do
    sign_in_billing
    number = seed_invoice
    post adjust_billing_invoice_path(number),
         params: { adjustment_type: "COMPENSATION", description: "破損補償", amount: "5000" }
    follow_redirect!
    expect(Billing::Public::BillingService.new.find_invoice(number).total_amount).to eq(94_000)
  end

  it "billing 以外のロールはアクセスできない" do
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "sales")
    post login_path, params: { username: user.username, password: "secret123" }
    get billing_invoices_path
    expect(response).to have_http_status(:forbidden)
  end
end
