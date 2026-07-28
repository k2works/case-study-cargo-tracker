# frozen_string_literal: true

require "rails_helper"

# ロール別ナビゲーション整合性・到達性（画面実装 DoD）。
RSpec.describe "ロール別ナビゲーション", type: :system do
  def login_as(user, password: "secret123")
    visit login_path
    fill_in "利用者 ID", with: user.username
    fill_in "パスワード", with: password
    click_button "ログイン"
  end

  describe "営業担当者（sales）" do
    let!(:user) { create(:user, :sales, username: "sales1", password: "secret123") }

    it "ダッシュボードから荷主登録へ到達できる（ロール別到達性）" do
      login_as(user)
      within(".dashboard-actions") { click_link "荷主を登録する" }
      expect(page).to have_current_path(new_shipper_path)
    end

    it "navbar に荷主登録・荷主一覧の導線が表示される" do
      login_as(user)
      within(".navbar") do
        expect(page).to have_link("荷主登録", href: new_shipper_path)
        expect(page).to have_link("荷主一覧", href: shippers_path)
      end
    end

    it "ダッシュボード/navbar から貨物予約一覧へ到達できる（US04 ロール別到達性）" do
      login_as(user)
      within(".navbar") { click_link "貨物予約" }
      expect(page).to have_current_path(bookings_path)
      expect(page).to have_content("貨物予約一覧")
    end
  end

  describe "営業以外（handler）" do
    let!(:user) { create(:user, username: "handler1", password: "secret123") }

    before { user.user_roles.create!(role: "handler") }

    it "navbar に荷主登録の導線が表示されない" do
      login_as(user)
      within(".navbar") { expect(page).to have_no_link("荷主登録") }
    end

    it "ダッシュボードに荷役作業員カードが表示される" do
      login_as(user)
      expect(page).to have_css('.dashboard-card[data-role="handler"]')
    end

    it "ダッシュボードから荷役作業登録へ到達できる（US15 ロール別到達性）" do
      login_as(user)
      within(".dashboard-actions") { click_link "荷役作業を登録する" }
      expect(page).to have_current_path(new_handling_event_path)
      expect(page).to have_content("荷役作業登録")
    end

    it "navbar から荷役管理へ到達できる" do
      login_as(user)
      within(".navbar") { click_link "荷役管理" }
      expect(page).to have_current_path(handling_events_path)
    end
  end

  describe "追跡管理者（tracker）" do
    let!(:user) { create(:user, username: "tracker1", password: "secret123") }

    before { user.user_roles.create!(role: "tracker") }

    it "ダッシュボードから貨物追跡入力フォームへ到達できる（US17 ロール別到達性）" do
      login_as(user)
      within(".dashboard-actions") { click_link "貨物を追跡する" }
      expect(page).to have_current_path(tracking_path)
      expect(page).to have_field("tracking_number") # プレースホルダではなく実フォーム
      expect(page).to have_button("追跡する")
    end

    it "navbar から貨物追跡へ到達できる" do
      login_as(user)
      within(".navbar") { click_link "貨物追跡" }
      expect(page).to have_current_path(tracking_path)
    end
  end
end
