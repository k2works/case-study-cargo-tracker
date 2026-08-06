# frozen_string_literal: true

require "rails_helper"

# US18: 追跡詳細の 30 秒 Turbo Frame 差分ポーリング（status エンドポイント）。
RSpec.describe "追跡ステータスポーリング（US18）", type: :request do
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
      "VALUES ('SHP-POLL', 'CORPORATE', '荷主', 'poll@example.com', NOW(), NOW())"
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

  it "status エンドポイントが現在状態の partial を返す" do
    sign_in_tracker
    tn = tracking_number
    get status_tracking_path(tn)
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("受領待ち") # NOT_RECEIVED ラベル
  end

  it "追跡詳細にポーリング用 Turbo Frame が埋め込まれる" do
    sign_in_tracker
    get tracking_detail_path(tracking_number)
    expect(response.body).to include("status_timeline")
    expect(response.body).to include("data-controller=\"polling\"")
  end
end
