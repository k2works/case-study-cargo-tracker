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
end
