# frozen_string_literal: true

require "rails_helper"

# US01: 輸送見積作成の HTTP フロー（営業担当者ロール）。
RSpec.describe "輸送見積作成（US01）", type: :request do
  def sign_in_sales
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "sales")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  # 外部経路システムをタイムアウトさせ、ローカルフォールバック（シード航海）へ誘導する。
  before { stub_request(:post, %r{/search}).to_timeout }

  # 直行便 JPTYO→USLAX をシードし、経路候補算出（フォールバック）で候補が返るようにする。
  def seed_voyage
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: "V001", carrier_name: "Ocean Line", supported_cargo_types: %w[GENERAL HAZARDOUS REFRIGERATED],
      movements: [ { departure_unlocode: "JPTYO", arrival_unlocode: "USLAX",
                     departure_date: "2026-09-01T09:00", arrival_date: "2026-09-15T18:00", seq_number: 1 } ]
    )
  end

  it "見積作成フォームを表示できる" do
    sign_in_sales
    get new_estimate_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("見積作成")
    expect(response.body).to include("data-controller=\"cargo-type\"") # 危険物申告の動的表示
  end

  it "見積を作成すると詳細へ遷移しルート候補と概算料金が表示される（US01）" do
    sign_in_sales
    seed_voyage
    post estimates_path, params: {
      origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-09-30",
      cargo_type: "GENERAL", weight_kg: "1000"
    }
    expect(response).to have_http_status(:redirect)
    follow_redirect!
    expect(response.body).to include("V001")        # 航海番号
    expect(response.body).to include("見積番号")     # 発行された見積番号ラベル
  end

  it "出発地と目的地が同一なら作成フォームへ戻る" do
    sign_in_sales
    seed_voyage
    post estimates_path, params: {
      origin: "JPTYO", destination: "JPTYO", arrival_deadline: "2026-09-30",
      cargo_type: "GENERAL", weight_kg: "1000"
    }
    follow_redirect!
    expect(response.body).to include("見積作成")
  end

  it "見積一覧を表示できる" do
    sign_in_sales
    get estimates_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("見積一覧")
  end

  it "sales 以外のロールはアクセスできない" do
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "handler")
    post login_path, params: { username: user.username, password: "secret123" }
    get estimates_path
    expect(response).to have_http_status(:forbidden)
  end
end
