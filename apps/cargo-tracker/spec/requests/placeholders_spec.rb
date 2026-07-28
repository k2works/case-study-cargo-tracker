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
    "sales" => [ "/bookings/1/route/edit",
                "/estimates", "/estimates/new", "/estimates/1", "/voyages", "/voyages/1", "/shippers" ],
    "tracker" => [ "/tracking", "/tracking/ABC123", "/exceptions", "/exceptions/new", "/handling_events" ],
    "handler" => [ "/handling_events", "/handling_events/new" ],
    "billing" => [ "/billing/invoices", "/billing/invoices/1" ],
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
