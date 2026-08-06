# frozen_string_literal: true

require "rails_helper"

# US19/US20: 例外管理の HTTP フロー（追跡管理者ロール）。
RSpec.describe "例外管理（US19 遅延 / US20 破損・紛失）", type: :request do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  def sign_in_tracker
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "tracker")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-EXCUI', 'CORPORATE', '荷主', 'excui@example.com', NOW(), NOW())"
    )
  end

  let(:tracking_number) do
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    booking_service.assign_itinerary(result.booking_id, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) }
    ])
    booking_service.confirm(result.booking_id)
    Tracking::Public::TrackingService.new.issue_tracking_number(result.booking_id).tracking_number
  end

  it "例外登録フォームを表示できる" do
    sign_in_tracker
    get new_exception_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("例外イベント登録")
  end

  it "遅延例外を登録すると一覧へ遷移し EXCEPTION 表示になる（US19）" do
    sign_in_tracker
    tn = tracking_number
    post exceptions_path, params: {
      tracking_number: tn, exception_type: "DELAY", description: "台風による遅延",
      location: "JPTYO", occurred_at: "2026-10-01T09:00"
    }
    expect(response).to redirect_to(exceptions_path)
    follow_redirect!
    expect(response.body).to include("例外を登録しました")
    expect(response.body).to include("遅延") # DELAY の日本語ラベル
  end

  it "破損例外を登録できる（US20）" do
    sign_in_tracker
    tn = tracking_number
    post exceptions_path, params: {
      tracking_number: tn, exception_type: "DAMAGE", description: "外装破損", occurred_at: "2026-10-02T09:00"
    }
    follow_redirect!
    expect(response.body).to include("破損")
  end

  it "存在しない追跡番号はエラーを返す" do
    sign_in_tracker
    post exceptions_path, params: { tracking_number: "TRK-NOEXIST", exception_type: "DELAY",
                                    description: "x", occurred_at: "2026-10-01T09:00" }
    follow_redirect!
    expect(response.body).to include("見つかりません")
  end

  it "対応報告を送信すると一覧へ遷移し完了メッセージが表示される（US19/US20 対応報告）" do
    sign_in_tracker
    tn = tracking_number
    post exceptions_path, params: { tracking_number: tn, exception_type: "DELAY",
                                    description: "遅延", occurred_at: "2026-10-01T09:00" }
    post report_exception_path(tn), params: { resolution_notes: "新到着予定日 12/5" }
    expect(response).to redirect_to(exceptions_path)
    follow_redirect!
    expect(response.body).to include("対応報告を送信しました")
  end

  it "対応対象がない追跡番号への対応報告はエラーを返す" do
    sign_in_tracker
    post report_exception_path("TRK-NOEXIST"), params: { resolution_notes: "x" }
    follow_redirect!
    expect(response.body).to include("見つかりません")
  end

  it "登録済み例外は一覧に緊急フラグ・種別付きで表示される（US20 緊急フラグ可視化）" do
    sign_in_tracker
    tn = tracking_number
    post exceptions_path, params: { tracking_number: tn, exception_type: "LOST",
                                    description: "紛失", occurred_at: "2026-10-01T09:00" }
    get exceptions_path
    expect(response.body).to include("紛失")
    expect(response.body).to include("緊急")
  end

  # 予約 → 追跡番号発行 → 例外登録を行い [booking_id, tracking_number] を返す。
  def register_exception_for_new_booking
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    booking_service.assign_itinerary(result.booking_id, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) }
    ])
    booking_service.confirm(result.booking_id)
    tn = Tracking::Public::TrackingService.new.issue_tracking_number(result.booking_id).tracking_number
    post exceptions_path, params: { tracking_number: tn, exception_type: "DELAY",
                                    description: "遅延", occurred_at: "2026-10-01T09:00" }
    [ result.booking_id, tn ]
  end

  it "該当予約に請求書があれば例外一覧から請求書へ遷移できる（T47c）" do
    sign_in_tracker
    booking_id, = register_exception_for_new_booking
    # 当該予約の請求書を用意する（テスト用に直接発行）。
    money = ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") }
    amounts = Billing::Domain::InvoiceAmounts.new(
      base: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.none,
      surcharge: money.call(0), tax: money.call(10_000), total: money.call(110_000)
    )
    Billing::Infrastructure::ActiveRecordInvoiceRepository.new.save(
      Billing::Domain::Invoice.generate(invoice_number: "INV-EXC01", booking_id: booking_id,
                                        shipper_id: 1, amounts: amounts, issued_at: Time.utc(2026, 11, 1))
    )
    get exceptions_path
    expect(response.body).to include("INV-EXC01")
    expect(response.body).to include(billing_invoice_path("INV-EXC01"))
  end

  it "該当予約に請求書がなければ導線は表示されない（nil 安全・T47c）" do
    sign_in_tracker
    register_exception_for_new_booking
    get exceptions_path
    expect(response).to have_http_status(:ok)
    expect(response.body).not_to include("/billing/invoices/INV-")
  end

  it "tracker 以外のロールはアクセスできない" do
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "handler")
    post login_path, params: { username: user.username, password: "secret123" }
    get exceptions_path
    expect(response).to have_http_status(:forbidden).or redirect_to(root_path)
  end
end
