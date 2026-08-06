# frozen_string_literal: true

require "rails_helper"

RSpec.describe "経路割り当て（US08 経路候補提示）", type: :system do
  let!(:sales) { create(:user, :sales, username: "sales1", password: "secret123") }

  let!(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "荷主太郎", email: "n@example.com", address: "東京"
    ).shipper_id
  end

  let!(:booking_id) do
    Booking::Public::CargoBookingService.new.book(
      shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: "1000",
      origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-12-01", description: "部品"
    ).booking_id
  end

  before do
    # 外部経路システムは未接続とし、自航海データからのフォールバック候補を検証する（US08・ADR-0004）
    stub_request(:post, %r{/search}).to_timeout

    # 対象ルートの航海を登録（外部 ACL 未接続時はこの自航海データからフォールバック候補が出る）
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: "V001", carrier_name: "Ocean Line", supported_cargo_types: %w[GENERAL],
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "USLAX",
                     departure_date: "2026-11-01T09:00", arrival_date: "2026-11-15T18:00", seq_number: 1 } ]
    )
  end

  def login
    visit login_path
    fill_in "利用者 ID", with: "sales1"
    fill_in "パスワード", with: "secret123"
    click_button "ログイン"
  end

  it "予約の出発地・目的地・期限から経路候補が提示される" do
    login
    visit edit_booking_route_path(booking_id)

    expect(page).to have_content("経路割り当て")
    expect(page).to have_content("V001") # 候補として自航海が提示される（フォールバック）
    expect(page).to have_content("所要")
  end

  it "予約詳細から経路割り当て画面へ到達できる（導線）" do
    login
    visit booking_path(booking_id)
    click_link "経路候補を見る（経路割り当て）"
    expect(page).to have_current_path(edit_booking_route_path(booking_id))
    expect(page).to have_content("経路割り当て")
  end
end
