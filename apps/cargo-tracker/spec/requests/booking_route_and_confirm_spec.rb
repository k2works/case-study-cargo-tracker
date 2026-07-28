# frozen_string_literal: true

require "rails_helper"

# US09/US11/US13: 経路割り当て → 経路提案済 → 予約確定の HTTP フロー（営業ロール）。
RSpec.describe "経路割り当て・予約確定フロー（US09/US11/US13）", type: :request do
  def sign_in_sales
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "sales")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  # cargos.shipper_id が参照する荷主。
  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-REQ', 'CORPORATE', 'テスト荷主', 'shipper@example.com', NOW(), NOW())"
    )
  end

  # 直行フォールバック候補を生む航海（JPTYO→USLAX）。
  def register_voyage
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: "V100", carrier_name: "Ocean Line", ship_name: "Star",
      supported_cargo_types: "GENERAL",
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "USLAX",
                     departure_date: "2026-09-01 08:00", arrival_date: "2026-11-20 18:00" } ]
    )
  end

  def create_cargo
    service = Booking::Public::CargoBookingService.new(
      shipper_existence_checker: Class.new { def exists?(_id) = true }.new
    )
    result = service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                          origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    service.assign_to_routing(result.booking_id)
    result.booking_id
  end

  it "経路候補を選択して紐付けると ROUTE_PROPOSED になり、確定で CONFIRMED になる" do
    # 外部経路システムは未接続とし、自航海データからのフォールバック候補で検証する（ADR-0004）。
    stub_request(:post, %r{/search}).to_timeout
    sign_in_sales
    register_voyage
    booking_id = create_cargo

    patch "/bookings/#{booking_id}/route", params: { candidate_index: 0 }
    expect(response).to have_http_status(:see_other)
    follow_redirect!
    expect(response.body).to include("経路提案済")

    # US12: 紐付けだけでは自動送信されず、営業の明示操作で荷主へ通知される
    post notify_route_booking_path(booking_id)
    follow_redirect!
    expect(response.body).to include("ROUTE_NOTIFIED")

    post confirm_booking_path(booking_id)
    follow_redirect!
    expect(response.body).to include("確定")
    expect(response.body).to include("TRACKING_REQUESTED") # 追跡番号発行依頼の通知記録

    # US14: 確定済みから追跡番号を発行すると TRACKING_ISSUED になり荷主へ通知される
    post issue_tracking_booking_path(booking_id)
    follow_redirect!
    expect(response.body).to include("追跡番号を発行しました")
    expect(response.body).to include("TRK-")
    expect(response.body).to include("TRACKING_ISSUED") # 荷主への追跡番号通知記録
  end

  it "経路紐付け後、通知前は ROUTE_NOTIFIED 記録が存在しない（US12 明示送信）" do
    stub_request(:post, %r{/search}).to_timeout
    sign_in_sales
    register_voyage
    booking_id = create_cargo

    patch "/bookings/#{booking_id}/route", params: { candidate_index: 0 }
    get booking_path(booking_id)
    expect(response.body).not_to include("ROUTE_NOTIFIED")
  end

  it "キャンセルすると荷主へキャンセル確認が通知される（US13）" do
    sign_in_sales
    booking_id = create_cargo # ROUTE_REQUESTED

    post cancel_booking_path(booking_id)
    follow_redirect!
    expect(response.body).to include("キャンセル")
    expect(response.body).to include("BOOKING_CANCELLED")
  end

  it "経路提案済からルート変更で差し戻すと経路設計中に戻る（US13）" do
    stub_request(:post, %r{/search}).to_timeout
    sign_in_sales
    register_voyage
    booking_id = create_cargo

    patch "/bookings/#{booking_id}/route", params: { candidate_index: 0 }
    post reroute_booking_path(booking_id)
    follow_redirect!
    expect(response.body).to include("経路設計中")
  end
end
