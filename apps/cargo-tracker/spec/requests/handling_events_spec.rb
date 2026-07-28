# frozen_string_literal: true

require "rails_helper"

# US15/US16: 荷役作業記録・引取の HTTP フロー（荷役作業員ロール）。
RSpec.describe "荷役作業記録（US15/US16）", type: :request do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  def sign_in_handler
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "handler")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-REQH', 'CORPORATE', '荷主', 'reqh@example.com', NOW(), NOW())"
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

  it "受領を記録すると成功し履歴に表示される" do
    sign_in_handler
    tn = tracking_number
    post handling_events_path, params: {
      tracking_number: tn, event_type: "RECEIVE", location: "JPTYO",
      completion_time: "2026-09-10T12:00", operator_name: "作業員A"
    }
    follow_redirect!
    expect(response.body).to include("荷役作業を記録しました")
    expect(response.body).to include("RECEIVE")
  end

  it "旅程外の港での積込は MISROUTED 警告付きで記録される" do
    sign_in_handler
    tn = tracking_number
    post handling_events_path, params: {
      tracking_number: tn, event_type: "LOAD", location: "CNSHA",
      completion_time: "2026-09-10T12:00", voyage_number: "V001"
    }
    follow_redirect!
    expect(response.body).to include("予定ルートと異なります")
  end

  it "存在しない追跡番号はエラーを表示する" do
    sign_in_handler
    post handling_events_path, params: {
      tracking_number: "TRK-DEADBEEF", event_type: "RECEIVE", location: "JPTYO",
      completion_time: "2026-09-10T12:00"
    }
    expect(response).to have_http_status(:unprocessable_entity)
    expect(response.body).to include("追跡番号が存在しません")
  end
end
