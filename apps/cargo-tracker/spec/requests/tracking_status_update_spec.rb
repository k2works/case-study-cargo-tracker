# frozen_string_literal: true

require "rails_helper"

# US17: 貨物状態手動更新の HTTP フロー（追跡管理者ロール）。
RSpec.describe "貨物状態手動更新（US17）", type: :request do
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
      "VALUES ('SHP-US17', 'CORPORATE', '荷主', 'us17@example.com', NOW(), NOW())"
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

  it "追跡詳細で現在の貨物情報を確認できる" do
    sign_in_tracker
    get tracking_detail_path(tracking_number)
    expect(response.body).to include(tracking_number)
    expect(response.body).to include("NOT_RECEIVED")
  end

  it "状態を手動更新すると追跡イベント履歴に記録される" do
    sign_in_tracker
    tn = tracking_number
    patch tracking_status_path(tn), params: {
      transport_status: "ONBOARD_CARRIER", location: "SGSIN", event_time: "2026-09-12T10:00"
    }
    follow_redirect!
    expect(response.body).to include("貨物状態を更新しました")
    expect(response.body).to include("ONBOARD_CARRIER")
    expect(response.body).to include("MANUAL_UPDATE") # 追跡イベント履歴
  end

  it "不正な状態値は拒否される" do
    sign_in_tracker
    tn = tracking_number
    patch tracking_status_path(tn), params: { transport_status: "UNKNOWN" }
    follow_redirect!
    expect(response.body).to include("状態が不正です")
  end
end
