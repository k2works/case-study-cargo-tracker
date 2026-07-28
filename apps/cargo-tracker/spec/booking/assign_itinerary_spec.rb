# frozen_string_literal: true

require "rails_helper"

# US09/US11: 経路（旅程）紐付けのアプリ層〜永続化の結合。
RSpec.describe "経路紐付け（US09/US11）" do
  # 荷主・港の存在確認は常に true を返すスタブ（本 spec の関心は旅程紐付けの往復）。
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  subject(:service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  let(:legs) do
    [ { load_location: "JPOSA", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) } ]
  end

  # cargos.shipper_id は shippers.id を参照する（ADR-0003）。FK を満たすため 1 件用意する。
  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-TEST', 'CORPORATE', 'テスト荷主', 'test@example.com', NOW(), NOW())"
    )
  end

  def book_and_request_routing
    result = service.book(
      shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
      origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1)
    )
    service.assign_to_routing(result.booking_id)
    result.booking_id
  end

  it "旅程を紐付けると ROUTE_PROPOSED になり legs が永続化・再構成される" do
    booking_id = book_and_request_routing
    expect(service.assign_itinerary(booking_id, legs)).to eq(:ok)

    view = service.find(booking_id)
    expect(view.route_proposed?).to be true
    expect(view.itinerary_legs.map(&:load_location)).to eq([ "JPOSA" ])
    expect(view.expected_arrival_time).to eq(Time.utc(2026, 11, 20, 18))
  end

  it "ルート仕様を満たさない旅程は :invalid を返し状態は変わらない" do
    booking_id = book_and_request_routing
    bad_legs = [ { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V009",
                   load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) } ]
    expect(service.assign_itinerary(booking_id, bad_legs)).to eq(:invalid)
    expect(service.find(booking_id).route_requested?).to be true
  end

  it "旅程が変わらない遷移（確定）では legs レコードを再作成しない（T25）" do
    booking_id = book_and_request_routing
    service.assign_itinerary(booking_id, legs)
    record = Booking::Infrastructure::CargoRecord.find_by(booking_id: booking_id)
    leg_ids_before = record.leg_records.pluck(:id)

    expect(service.confirm(booking_id)).to eq(:ok)

    leg_ids_after = record.reload.leg_records.pluck(:id)
    expect(leg_ids_after).to eq(leg_ids_before)
  end
end
