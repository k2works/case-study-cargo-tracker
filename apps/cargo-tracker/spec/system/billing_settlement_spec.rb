# frozen_string_literal: true

require "rails_helper"

# US21/US22/US23: 料金算出→法人割引→精算の業務シナリオ E2E（終盤アウトサイドイン）。
RSpec.describe "料金算出から精算までの業務シナリオ（US21/US22/US23）", type: :system do
  let(:always_present) { Class.new { def exists?(_id) = true }.new }
  let(:booking_service) do
    Booking::Public::CargoBookingService.new(
      shipper_existence_checker: always_present, location_existence_checker: always_present
    )
  end

  def login_as(user, password: "secret123")
    visit login_path
    fill_in "利用者 ID", with: user.username
    fill_in "パスワード", with: password
    click_button "ログイン"
  end

  # 法人荷主 + DELIVERED 予約を用意し、予約番号を返す。
  def delivered_booking
    shipper_id = ActiveRecord::Base.connection.insert(
      "INSERT INTO shippers (shipper_code, shipper_type, name, email, discount_rate, created_at, updated_at) " \
      "VALUES ('SHP-E2E', 'CORPORATE', '法人荷主', 'e2e@example.com', 0.10, NOW(), NOW())"
    )
    result = booking_service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: 1000,
                                  origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
    booking_service.assign_to_routing(result.booking_id)
    booking_service.assign_itinerary(result.booking_id, [
      { load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18) }
    ])
    booking_service.confirm(result.booking_id)
    tracking = Tracking::Public::TrackingService.new
    tracking.issue_tracking_number(result.booking_id)
    handling = Handling::Public::HandlingService.new
    handling.register(tracking_number: booking_service.find(result.booking_id).tracking_number,
                      event_type: "LOAD", location: "JPTYO", completion_time: Time.utc(2026, 9, 2, 10),
                      voyage_number: "V001", operator_name: "作業員")
    handling.register(tracking_number: booking_service.find(result.booking_id).tracking_number,
                      event_type: "CLAIM", location: "USLAX", completion_time: Time.utc(2026, 11, 21, 10),
                      operator_name: "作業員", recipient: { name: "受取人", confirmation_code: "OK123" })
    result.booking_id
  end

  let!(:billing_user) do
    u = create(:user, username: "billing_e2e", password: "secret123")
    u.user_roles.create!(role: "billing")
    u
  end

  it "経理が料金算出→割引確認→入金確認で精算を完了する" do
    booking_id = delivered_booking
    expect(booking_service.find(booking_id).delivered?).to be true

    login_as(billing_user)
    within(".navbar") { click_link "請求管理" }

    # 料金算出（US21/US22）
    fill_in "booking_id", with: booking_id
    click_button "料金を算出する"

    # 割引根拠と消費税が表示される（US22）
    expect(page).to have_content("基本料金")
    expect(page).to have_content("割引")
    expect(page).to have_content("消費税（10%）")

    # 入金確認で精算完了（US23）。rack_test は turbo_confirm を無視して直接送信する。
    click_button "入金を確認する"
    expect(page).to have_content("入金を確認しました")
    expect(page).to have_content("精算済")

    # 予約も精算済（SETTLED）に同期される
    expect(booking_service.find(booking_id).status_value).to eq("SETTLED")
  end
end
