# frozen_string_literal: true

require "rails_helper"

# US15/US16: 荷役作業記録・引取のアプリ層〜BC 越境（追跡/予約状態同期・通知）の結合。
RSpec.describe "荷役作業記録（US15/US16）" do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end
  let(:tracking) { Tracking::Public::TrackingService.new }
  subject(:handling) { Handling::Public::HandlingService.new }

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-HDL', 'CORPORATE', '荷主', 'hdl@example.com', NOW(), NOW())"
    )
  end

  # 追跡番号発行済み（TRACKING_ISSUED）の予約を用意し、追跡番号を返す。
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

  def register(event_type:, location:, voyage: "V001", **extra)
    handling.register(tracking_number: tracking_number, event_type: event_type, location: location,
                      completion_time: Time.utc(2026, 9, 10, 12), voyage_number: voyage,
                      operator_name: "作業員A", **extra)
  end

  it "追跡番号が存在しなければエラー（:not_found）" do
    result = handling.register(tracking_number: "TRK-DEADBEEF", event_type: "RECEIVE", location: "JPTYO",
                               completion_time: Time.utc(2026, 9, 10, 12))
    expect(result.status).to eq(:not_found)
  end

  it "受領→積込で追跡状態が反映され BookingStatus が IN_TRANSIT になる（US15）" do
    expect(register(event_type: "RECEIVE", location: "JPTYO", voyage: nil).status).to eq(:ok)
    expect(register(event_type: "LOAD", location: "JPTYO").status).to eq(:ok)

    expect(tracking.find_by_booking_id(booking_id_of).transport_status).to eq("LOADED")
    expect(booking_service.find(booking_id_of).status_value).to eq("IN_TRANSIT")
  end

  it "旅程外の港での積込は MISROUTED 警告を返す（記録は継続・US15）" do
    result = register(event_type: "LOAD", location: "CNSHA")
    expect(result.status).to eq(:ok)
    expect(result.route_check).to eq(:misrouted)
  end

  it "引取（CLAIM）を荷受人確認付きで記録すると DELIVERED になる（US16）" do
    register(event_type: "RECEIVE", location: "JPTYO", voyage: nil)
    register(event_type: "LOAD", location: "JPTYO")
    claim = register(event_type: "CLAIM", location: "USLAX", voyage: nil,
                     recipient_name: "山田", confirmation_code: "OK123")
    expect(claim.status).to eq(:ok)
    expect(booking_service.find(booking_id_of).status_value).to eq("DELIVERED")
    expect(tracking.find_by_booking_id(booking_id_of).transport_status).to eq("CLAIMED")
  end

  it "引取で荷受人確認がなければ :invalid（US16）" do
    register(event_type: "RECEIVE", location: "JPTYO", voyage: nil)
    register(event_type: "LOAD", location: "JPTYO")
    result = register(event_type: "CLAIM", location: "USLAX", voyage: nil)
    expect(result.status).to eq(:invalid)
  end

  # tracking_number の予約番号を取得するヘルパ（tracking_number 発行時の予約）。
  def booking_id_of
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    ).find_by_tracking_number(tracking_number).booking_id
  end
end
