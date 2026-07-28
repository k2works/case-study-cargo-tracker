# frozen_string_literal: true

require "rails_helper"

# US25: 既存航海スケジュール更新の差分確認フロー。
RSpec.describe "航海スケジュール更新の差分確認（US25）", type: :request do
  def sign_in_sales
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "sales")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  before do
    sign_in_sales
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: "V200", carrier_name: "Ocean Line", ship_name: "Star",
      supported_cargo_types: %w[GENERAL],
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "USLAX",
                     departure_date: "2026-11-01T09:00", arrival_date: "2026-11-15T18:00", seq_number: 1 } ]
    )
  end

  it "更新内容の確認画面に差分（変更前・変更後）が表示される" do
    post confirm_update_voyage_path("V200"), params: {
      carrier_name: "Ocean Line", ship_name: "Star", origin: "JPTYO", destination: "USLAX",
      departure_date: "2026-11-02T09:00", arrival_date: "2026-11-16T18:00", supported_cargo_types: %w[GENERAL]
    }
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("変更前")
    expect(response.body).to include("2026-11-15 18:00") # 変更前の到着日時
    expect(response.body).to include("2026-11-16T18:00") # 変更後の到着日時（入力値）
  end

  it "日時を変更せず他項目だけ変更した場合、日時は差分に出ない（M1）" do
    post confirm_update_voyage_path("V200"), params: {
      carrier_name: "New Line", ship_name: "Star", origin: "JPTYO", destination: "USLAX",
      departure_date: "2026-11-01T09:00", arrival_date: "2026-11-15T18:00", supported_cargo_types: %w[GENERAL]
    }
    expect(response.body).to include("運送会社") # 変更あり
    expect(response.body).not_to include("出発日時") # 日時は無変更なので差分に出ない
    expect(response.body).not_to include("到着日時")
  end

  it "確認後に更新すると上書きされ検索結果に反映される" do
    patch voyage_path("V200"), params: {
      carrier_name: "Ocean Line", ship_name: "Star", origin: "JPTYO", destination: "USLAX",
      departure_date: "2026-11-02T09:00", arrival_date: "2026-11-16T18:00", supported_cargo_types: %w[GENERAL]
    }
    follow_redirect!
    expect(response.body).to include("航海スケジュールを更新しました")

    updated = Routing::Public::VoyageDirectory.new.find("V200")
    expect(updated.arrival_date).to eq(Time.zone.parse("2026-11-16T18:00"))
  end
end
