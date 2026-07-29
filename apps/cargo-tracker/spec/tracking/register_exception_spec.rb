# frozen_string_literal: true

require "rails_helper"

# US19/US20: 例外処理のアプリ層〜永続化・BC 越境・通知結線の結合。
RSpec.describe "追跡例外の登録（US19 遅延 / US20 破損・紛失）" do
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
      "VALUES ('SHP-EXC', 'CORPORATE', 'テスト荷主', 'exc@example.com', NOW(), NOW())"
    )
  end

  # 追跡番号発行済みの貨物を用意する。
  def issued_tracking_number
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    legs = [ { load_location: "JPOSA", unload_location: "USLAX", voyage_number: "V001",
               load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) } ]
    booking_service.assign_itinerary(result.booking_id, legs)
    booking_service.confirm(result.booking_id)
    tracking.issue_tracking_number(result.booking_id).tracking_number
  end

  before do
    DomainEvents.reset!
    Booking::Public::NotificationWiring.install!
    Tracking::Public::TrackingWiring.install!
  end

  after { DomainEvents.reset! }

  it "遅延例外を登録すると輸送状態が EXCEPTION になり荷主へ通知される（US19）" do
    number = issued_tracking_number
    result = tracking.register_exception(
      number, exception_type: "DELAY", occurred_at: Time.utc(2026, 10, 1, 9),
      description: "台風による遅延", location: "JPOSA"
    )

    expect(result.status).to eq(:ok)
    expect(tracking.find_by_tracking_number(number).transport_status).to eq("EXCEPTION")

    notifications = Shared::Public::NotificationRecorder.new.for(notifiable_type: "Cargo", notifiable_id: result.booking_id)
    expect(notifications.map(&:event_type)).to include("EXCEPTION_DELAY")
  end

  it "紛失例外は管理職への escalation 通知を伴う（US20）" do
    number = issued_tracking_number
    result = tracking.register_exception(
      number, exception_type: "LOST", occurred_at: Time.utc(2026, 10, 2, 9), description: "紛失"
    )

    expect(result.status).to eq(:ok)
    notifications = Shared::Public::NotificationRecorder.new.for(notifiable_type: "Cargo", notifiable_id: result.booking_id)
    expect(notifications.map(&:recipient_type)).to include("MANAGER")
  end

  it "存在しない追跡番号は :not_found を返す" do
    expect(tracking.register_exception("TRK-NOEXIST", exception_type: "DELAY",
                                       occurred_at: Time.utc(2026, 10, 1), description: "x").status).to eq(:not_found)
  end

  it "未知の例外種別は :invalid を返す" do
    number = issued_tracking_number
    expect(tracking.register_exception(number, exception_type: "UNKNOWN",
                                       occurred_at: Time.utc(2026, 10, 1), description: "x").status).to eq(:invalid)
  end

  it "登録済み例外を対応報告で解決すると発生前状態に復帰する（US19 対応報告）" do
    number = issued_tracking_number
    tracking.register_exception(number, exception_type: "DELAY",
                                occurred_at: Time.utc(2026, 10, 1), description: "遅延")
    result = tracking.resolve_exception(number, resolution_notes: "新到着予定日 12/5")

    expect(result.status).to eq(:ok)
    expect(tracking.find_by_tracking_number(number).transport_status).to eq("NOT_RECEIVED")
  end
end
