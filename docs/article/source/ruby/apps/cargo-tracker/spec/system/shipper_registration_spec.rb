# frozen_string_literal: true

require "rails_helper"

RSpec.describe "荷主登録（US02/US03）", type: :system do
  let!(:sales) { create(:user, :sales, username: "sales1", password: "secret123") }

  def login
    visit login_path
    fill_in "利用者 ID", with: "sales1"
    fill_in "パスワード", with: "secret123"
    click_button "ログイン"
  end

  describe "個人荷主の登録（US02）" do
    it "個人荷主を登録すると荷主コードが発行される" do
      login
      visit new_shipper_path
      choose "個人"
      fill_in "氏名／社名", with: "山田太郎"
      fill_in "住所", with: "東京都千代田区"
      fill_in "メールアドレス", with: "yamada@example.com"
      fill_in "連絡先", with: "03-1234-5678"
      click_button "登録する"

      expect(page).to have_content("荷主を登録しました")
      expect(page).to have_content(/SHP-/)
      expect(page).to have_content("山田太郎")
    end

    it "メール重複時は既存荷主を提示する" do
      login
      Shipper::Application::RegisterShipper.new(
        repository: Shipper::Infrastructure::ActiveRecordShipperRepository.new
      ).call(
        shipper_type: "INDIVIDUAL", name: "既存太郎", email: "dup@example.com", address: "東京"
      )

      visit new_shipper_path
      choose "個人"
      fill_in "氏名／社名", with: "新規太郎"
      fill_in "メールアドレス", with: "dup@example.com"
      click_button "登録する"

      expect(page).to have_content("既に登録されています")
      expect(page).to have_content("既存太郎")
    end
  end

  describe "法人荷主の登録（US03）" do
    it "法人を選択し契約情報を入力して登録できる" do
      login
      visit new_shipper_path
      choose "法人"
      fill_in "氏名／社名", with: "株式会社サンプル"
      fill_in "メールアドレス", with: "corp@example.com"
      fill_in "契約番号", with: "C-0001"
      fill_in "割引率（%）", with: "15"
      click_button "登録する"

      expect(page).to have_content("荷主を登録しました")
      expect(page).to have_content("株式会社サンプル")
    end

    it "割引率が 30% を超えるとエラーになる" do
      login
      visit new_shipper_path
      choose "法人"
      fill_in "氏名／社名", with: "株式会社サンプル"
      fill_in "メールアドレス", with: "corp2@example.com"
      fill_in "契約番号", with: "C-0002"
      fill_in "割引率（%）", with: "50"
      click_button "登録する"

      expect(page).to have_content("割引率")
    end
  end

  describe "アクセス制御" do
    it "営業担当者以外は荷主登録にアクセスできない（403）" do
      handler = create(:user, username: "handler1", password: "secret123")
      handler.user_roles.create!(role: "handler")
      visit login_path
      fill_in "利用者 ID", with: "handler1"
      fill_in "パスワード", with: "secret123"
      click_button "ログイン"

      visit new_shipper_path
      expect(page).to have_content("403")
    end
  end
end
