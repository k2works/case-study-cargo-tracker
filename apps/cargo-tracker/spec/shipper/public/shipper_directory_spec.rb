# frozen_string_literal: true

require "rails_helper"

# Shipper Context の公開 API（他コンテキスト・アプリ層向けの参照）。
RSpec.describe Shipper::Public::ShipperDirectory do
  subject(:directory) { described_class.new }

  let(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "山田太郎", email: "yamada@example.com", address: "東京"
    ).shipper_id
  end

  describe "#exists?" do
    it "登録済みの荷主 ID なら true" do
      expect(directory.exists?(shipper_id)).to be true
    end

    it "未登録の荷主 ID なら false" do
      expect(directory.exists?(-1)).to be false
    end
  end

  describe "#find" do
    it "荷主 ID で公開ビュー（id/コード/氏名/メール）を取得できる" do
      view = directory.find(shipper_id)
      expect(view.name).to eq("山田太郎")
      expect(view.email).to eq("yamada@example.com")
      expect(view.id).to eq(shipper_id)
    end

    it "未登録なら nil" do
      expect(directory.find(-1)).to be_nil
    end
  end
end
