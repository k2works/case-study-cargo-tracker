# frozen_string_literal: true

require "rails_helper"

RSpec.describe "認証ガード（US26）", type: :request do
  describe "未認証アクセス" do
    it "業務機能にアクセスするとログイン画面へ誘導される" do
      get root_path
      expect(response).to redirect_to(login_path)
    end

    it "荷主登録画面も未認証ではログイン画面へ誘導される" do
      get new_shipper_path
      expect(response).to redirect_to(login_path)
    end
  end

  describe "ログアウトの監査ログ（US27）" do
    let!(:user) { create(:user, :sales, username: "alice", password: "secret123") }

    it "ログアウト日時がログに記録される" do
      post login_path, params: { username: "alice", password: "secret123" }
      allow(Rails.logger).to receive(:info).and_call_original
      delete logout_path
      expect(response).to redirect_to(login_path)
      expect(Rails.logger).to have_received(:info).with(/ログアウト.*alice/)
    end
  end
end
