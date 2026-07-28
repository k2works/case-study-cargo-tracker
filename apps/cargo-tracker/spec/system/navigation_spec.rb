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
  end
end
