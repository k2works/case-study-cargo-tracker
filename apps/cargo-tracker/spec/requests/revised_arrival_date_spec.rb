# frozen_string_literal: true

require "rails_helper"

# T37: 遅延対応の新到着予定日を構造化し公開追跡の推定到着日へ反映する。
RSpec.describe "遅延対応の新到着予定日（T37・US18/US19）", type: :request do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-T37', 'CORPORATE', '荷主', 't37@example.com', NOW(), NOW())"
    )
  end

  # 遅延例外を登録済みの追跡番号・予約番号を返す。
  def tracking_with_delay
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    booking_service.assign_itinerary(result.booking_id, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) }
    ])
    booking_service.confirm(result.booking_id)
    tn = Tracking::Public::TrackingService.new.issue_tracking_number(result.booking_id).tracking_number
    Tracking::Public::TrackingService.new.register_exception(
      tn, exception_type: "DELAY", occurred_at: Time.utc(2026, 10, 1), description: "台風遅延"
    )
    [ tn, result.booking_id ]
  end

  it "対応報告の新到着予定日が公開追跡の推定到着日に反映される" do
    tn, = tracking_with_delay
    # 対応報告で新到着予定日 12/15 を構造化入力
    Tracking::Public::TrackingService.new.resolve_exception(
      tn, resolution_notes: "経路変更で対応", revised_arrival_date: Date.new(2026, 12, 15)
    )

    get public_tracking_detail_path(tn)
    expect(response.body).to include("2026-12-15") # 新到着予定日が推定到着日に優先反映
  end
end
