# frozen_string_literal: true

require "rails_helper"

RSpec.describe "貨物予約（US04/US05/US06）", type: :system do
  let!(:sales) { create(:user, :sales, username: "sales1", password: "secret123") }

  let!(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "荷主太郎", email: "nushi@example.com", address: "東京"
    ).shipper_id
  end

  def login
    visit login_path
    fill_in "利用者 ID", with: "sales1"
    fill_in "パスワード", with: "secret123"
    click_button "ログイン"
  end

  def fill_common_fields
    fill_in "荷主 ID", with: shipper_id
    fill_in "重量（kg）", with: "1200.5"
    fill_in "出発地（UN/LOCODE）", with: "JPOSA"
    fill_in "目的地（UN/LOCODE）", with: "USLAX"
    fill_in "希望着日", with: "2026-12-01"
  end

  describe "一般貨物の予約登録（US04）" do
    it "予約を登録すると予約番号が発行され仮受付になる" do
      login
      visit new_booking_path
      select "一般", from: "貨物種別"
      fill_common_fields
      fill_in "品名", with: "自動車部品"
      click_button "予約する"

      expect(page).to have_content("予約を登録しました")
      expect(page).to have_content(/BKG-/)
      expect(page).to have_content("仮受付")
    end

    it "存在しない荷主 ID では登録できない" do
      login
      visit new_booking_path
      select "一般", from: "貨物種別"
      fill_in "荷主 ID", with: "999999"
      fill_in "重量（kg）", with: "10"
      fill_in "出発地（UN/LOCODE）", with: "JPOSA"
      fill_in "目的地（UN/LOCODE）", with: "USLAX"
      fill_in "希望着日", with: "2026-12-01"
      click_button "予約する"

      expect(page).to have_content("荷主が存在しません")
    end
  end

  describe "危険物貨物の予約登録（US05）" do
    it "危険物を選択し申告情報を入力して登録できる" do
      login
      visit new_booking_path
      select "危険物", from: "貨物種別"
      fill_common_fields
      fill_in "危険物クラス", with: "3"
      fill_in "UN 番号", with: "UN1203"
      fill_in "正式輸送品名", with: "GASOLINE"
      click_button "予約する"

      expect(page).to have_content("予約を登録しました")
    end

    it "危険物で申告情報が空だとエラーになる" do
      login
      visit new_booking_path
      select "危険物", from: "貨物種別"
      fill_common_fields
      click_button "予約する"

      expect(page).to have_content("危険物申告")
    end
  end

  describe "経路設計者への引き渡し（US06）" do
    it "予約詳細から引き渡すと経路設計中になる" do
      login
      visit new_booking_path
      select "一般", from: "貨物種別"
      fill_common_fields
      click_button "予約する"

      click_button "経路設計者へ引き渡す"

      expect(page).to have_content("経路設計を依頼しました")
      expect(page).to have_content("経路設計中")
    end
  end
end
