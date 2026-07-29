# frozen_string_literal: true

require "rails_helper"

# US18: 公開貨物追跡（認証不要）。荷主・荷受人が URL 共有で照会する。
RSpec.describe "公開貨物追跡（US18・認証不要）", type: :request do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  let(:shipper_id) do
    ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, created_at, updated_at) " \
      "VALUES ('SHP-PUB', 'CORPORATE', '荷主', 'pub@example.com', NOW(), NOW())"
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

  it "ログインなしで追跡入力フォームを表示できる" do
    get public_tracking_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("追跡番号")
  end

  it "ログインなしで追跡番号から現在状態を照会できる" do
    tn = tracking_number
    get public_tracking_detail_path(tn)
    expect(response).to have_http_status(:ok)
    expect(response.body).to include(tn)
    expect(response.body).to include("受領待ち") # NOT_RECEIVED ラベル
  end

  it "存在しない追跡番号は見つからないメッセージを表示する" do
    get public_tracking_detail_path("TRK-NOEXIST")
    expect(response.body).to include("見つかりません")
  end

  it "公開ページは荷主の個人情報（メールアドレス）を露出しない" do
    tn = tracking_number
    get public_tracking_detail_path(tn)
    expect(response.body).not_to include("pub@example.com")
  end

  it "推定到着日（確定経路の最終 leg 到着時刻）を表示する（US18）" do
    tn = tracking_number
    get public_tracking_detail_path(tn)
    expect(response.body).to include("推定到着")
    expect(response.body).to include("2026-11-20") # 最終 leg の unload_time
  end
end
