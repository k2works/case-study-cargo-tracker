# frozen_string_literal: true

require "rails_helper"

# US05 の「貨物種別を選択すると入力欄が表示される」動的表示を JS ドライバで検証する（T11）。
RSpec.describe "貨物種別の動的表示（US05）", :js, type: :system do
  let!(:sales) { create(:user, :sales, username: "sales1", password: "secret123") }

  before do
    visit login_path
    fill_in "利用者 ID", with: "sales1"
    fill_in "パスワード", with: "secret123"
    click_button "ログイン"
    expect(page).to have_content("ダッシュボード") # Turbo 遷移の完了を待つ
    visit new_booking_path
    expect(page).to have_css("h1", text: "貨物予約登録")
  end

  it "一般を選ぶと危険物・冷凍の入力欄が非表示" do
    select "一般", from: "貨物種別"
    expect(page).to have_no_field("危険物クラス")
    expect(page).to have_no_field("最低温度")
  end

  it "危険物を選ぶと危険物申告欄が表示される" do
    select "危険物", from: "貨物種別"
    expect(page).to have_field("危険物クラス")
    expect(page).to have_no_field("最低温度")
  end

  it "冷凍・冷蔵を選ぶと温度条件欄が表示される" do
    select "冷凍・冷蔵", from: "貨物種別"
    expect(page).to have_field("最低温度")
    expect(page).to have_no_field("危険物クラス")
  end
end
