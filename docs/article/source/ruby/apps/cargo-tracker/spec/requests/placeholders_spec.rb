# frozen_string_literal: true

require "rails_helper"

# ウォーキングスケルトン：全ルートのプレースホルダ画面とロール制御。
RSpec.describe "プレースホルダ画面（全ルート）", type: :request do
  def sign_in(user)
    post login_path, params: { username: user.username, password: "secret123" }
  end

  def user_with(role)
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: role)
    user
  end

  # ロールごとに到達できるべき GET 画面
  ROLE_SCREENS = {
    # /estimates 系は US01 実装済（/estimates/1 は未検出時リダイレクト）のためプレースホルダ一覧から外し、
    # estimates_spec で検証する。
    "sales" => [ "/shippers" ],
    # /tracking は追跡番号入力フォーム（実装済）、/tracking/:tn は US17 実装済（未検出時リダイレクト）のため
    # プレースホルダ一覧からは外し、tracking_status_update_spec で検証する。
    "tracker" => [ "/tracking", "/exceptions", "/exceptions/new", "/handling_events" ],
    "handler" => [ "/handling_events", "/handling_events/new" ],
    # /billing/invoices 系は US21-23 実装済（/billing/invoices/1 は未検出時リダイレクト）のため
    # プレースホルダ一覧から外し、billing_invoices_spec で検証する。
    "billing" => [],
    "admin" => [ "/admin/discount_policies", "/admin/discount_policies/new", "/admin/discount_policies/1/edit" ]
  }.freeze

  ROLE_SCREENS.each do |role, paths|
    context "#{role} ロール" do
      before { sign_in(user_with(role)) }

      paths.each do |path|
        it "#{path} が 200 で表示される" do
          get path
          expect(response).to have_http_status(:ok)
        end
      end
    end
  end

  describe "ロール外アクセス" do
    it "sales は請求管理にアクセスできない（403）" do
      sign_in(user_with("sales"))
      get "/billing/invoices"
      expect(response).to have_http_status(:forbidden)
    end

    it "handler は管理設定にアクセスできない（403）" do
      sign_in(user_with("handler"))
      get "/admin/discount_policies"
      expect(response).to have_http_status(:forbidden)
    end
  end

  describe "公開追跡（認証不要）" do
    it "未認証でも公開追跡入力に到達できる" do
      get "/public/tracking"
      expect(response).to have_http_status(:ok)
    end

    it "未認証でも公開追跡照会に到達できる" do
      get "/public/tracking/ABC123"
      expect(response).to have_http_status(:ok)
    end
  end
end
