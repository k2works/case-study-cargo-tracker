# frozen_string_literal: true

require "rails_helper"

# US14: 追跡番号発行のアプリ層〜永続化・BC 越境の結合。
RSpec.describe "追跡番号発行（US14）" do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end
  subject(:tracking) { Tracking::Public::TrackingService.new }

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-TRK', 'CORPORATE', 'テスト荷主', 'trk@example.com', NOW(), NOW())"
    )
  end

  # CONFIRMED 状態の予約を用意する。
  def confirmed_booking
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    legs = [ { load_location: "JPOSA", unload_location: "USLAX", voyage_number: "V001",
               load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) } ]
    booking_service.assign_itinerary(result.booking_id, legs)
    booking_service.confirm(result.booking_id)
    result.booking_id
  end

  it "確定済み予約に一意の追跡番号を発行し TRACKING_ISSUED にする" do
    booking_id = confirmed_booking
    result = tracking.issue_tracking_number(booking_id)

    expect(result.status).to eq(:ok)
    expect(result.tracking_number).to match(/\ATRK-/)
    expect(booking_service.find(booking_id).tracking_issued?).to be true
    expect(booking_service.find(booking_id).tracking_number).to eq(result.tracking_number)
    expect(tracking.find_by_booking_id(booking_id).transport_status).to eq("NOT_RECEIVED")
  end

  it "確定済みでない予約には発行できない（:invalid）" do
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    expect(tracking.issue_tracking_number(result.booking_id).status).to eq(:invalid)
  end

  it "既発行の予約は :already_issued を返す" do
    booking_id = confirmed_booking
    first = tracking.issue_tracking_number(booking_id)
    again = tracking.issue_tracking_number(booking_id)
    expect(again.status).to eq(:already_issued)
    expect(again.tracking_number).to eq(first.tracking_number)
  end

  it "Booking は TRACKING_ISSUED だが Tracking 未作成の宙吊り状態を冪等回復する（M2）" do
    booking_id = confirmed_booking
    # 前回の途中失敗を再現: Booking 側だけ追跡番号発行済みにし、Tracking 活動は作らない。
    booking_service.issue_tracking_number(booking_id, "TRK-0000ABCD")
    expect(Tracking::Public::TrackingService.new.find_by_booking_id(booking_id)).to be_nil

    result = tracking.issue_tracking_number(booking_id)
    expect(result.status).to eq(:ok)
    expect(result.tracking_number).to eq("TRK-0000ABCD") # 保持済み番号で復元（新規採番しない）
    expect(tracking.find_by_booking_id(booking_id).tracking_number).to eq("TRK-0000ABCD")
  end
end
