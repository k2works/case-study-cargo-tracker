# frozen_string_literal: true

require "rails_helper"

RSpec.describe "航海スケジュール（US24/US25/US07）", type: :system do
  let!(:sales) { create(:user, :sales, username: "sales1", password: "secret123") }

  def login
    visit login_path
    fill_in "利用者 ID", with: "sales1"
    fill_in "パスワード", with: "secret123"
    click_button "ログイン"
  end

  def register_voyage(number:, origin:, dest:, dep:, arr:, types: %w[GENERAL])
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: number, carrier_name: "Ocean Line", ship_name: "Star",
      supported_cargo_types: types,
      movements: [ { departure_unlocode: origin, arrival_unlocode: dest,
                     departure_date: dep, arrival_date: arr, seq_number: 1 } ]
    )
  end

  describe "航海スケジュール登録（US24）" do
    it "航海を登録すると一覧に表示される" do
      login
      visit new_voyage_path
      fill_in "航海番号", with: "V100"
      fill_in "運送会社", with: "Ocean Line"
      fill_in "船名", with: "Pacific Star"
      fill_in "出発港（UN/LOCODE）", with: "JPTYO"
      fill_in "到着港（UN/LOCODE）", with: "USLAX"
      fill_in "出発日時", with: "2026-11-01T09:00"
      fill_in "到着日時", with: "2026-11-15T18:00"
      click_button "登録する"

      expect(page).to have_content("航海スケジュールを登録しました")
      expect(page).to have_content("V100")
    end

    it "出発日が到着日より後だとエラーになる" do
      login
      visit new_voyage_path
      fill_in "航海番号", with: "V101"
      fill_in "運送会社", with: "Ocean Line"
      fill_in "出発港（UN/LOCODE）", with: "JPTYO"
      fill_in "到着港（UN/LOCODE）", with: "USLAX"
      fill_in "出発日時", with: "2026-11-20T09:00"
      fill_in "到着日時", with: "2026-11-01T18:00"
      click_button "登録する"

      expect(page).to have_content("出発日")
    end
  end

  describe "航海スケジュール検索（US07）" do
    before do
      register_voyage(number: "V001", origin: "JPTYO", dest: "USLAX", dep: "2026-11-01T09:00", arr: "2026-11-15T18:00", types: %w[GENERAL])
      register_voyage(number: "V002", origin: "JPTYO", dest: "USLAX", dep: "2026-12-01T09:00", arr: "2026-12-15T18:00", types: %w[GENERAL HAZARDOUS])
    end

    it "出発地・目的地で検索して該当航海を表示する" do
      login
      visit voyages_path
      fill_in "出発地", with: "JPTYO"
      fill_in "目的地", with: "USLAX"
      click_button "検索"

      expect(page).to have_content("V001")
      expect(page).to have_content("V002")
    end

    it "危険物で絞り込むと対応航海のみ表示する" do
      login
      visit voyages_path
      fill_in "出発地", with: "JPTYO"
      fill_in "目的地", with: "USLAX"
      select "危険物", from: "貨物種別"
      click_button "検索"

      expect(page).to have_content("V002")
      expect(page).to have_no_content("V001")
    end
  end

  describe "航路一覧からの到達性" do
    it "navbar から航路管理へ到達できる（sales が経路設計者を代替）" do
      login
      within(".navbar") { click_link "航路管理" }
      expect(page).to have_current_path(voyages_path)
      expect(page).to have_content("航路")
    end
  end
end
