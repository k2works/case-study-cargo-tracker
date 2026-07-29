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

  it "tracker 以外のロールはアクセスできない" do
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "handler")
    post login_path, params: { username: user.username, password: "secret123" }
    get exceptions_path
    expect(response).to have_http_status(:forbidden).or redirect_to(root_path)
  end
end
