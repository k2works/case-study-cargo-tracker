# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shared::Public::LocationDirectory do
  subject(:directory) { described_class.new }

  before do
    directory.register(unlocode: "JPTYO", name: "Tokyo", country_code: "JP", time_zone: "Asia/Tokyo")
    directory.register(unlocode: "USLAX", name: "Los Angeles", country_code: "US")
  end

  describe "#register" do
    it "同一 UN/LOCODE は重複登録しない（冪等）" do
      expect { directory.register(unlocode: "JPTYO", name: "東京") }.not_to change { directory.all.size }
    end
  end

  describe "#exists?" do
    it "登録済み UN/LOCODE なら true" do
      expect(directory.exists?("JPTYO")).to be true
    end

    it "未登録なら false" do
      expect(directory.exists?("NLRTM")).to be false
    end
  end

  describe "#find" do
    it "UN/LOCODE から Location 値オブジェクトを取得できる" do
      loc = directory.find("JPTYO")
      expect(loc).to be_a(Shared::Domain::Location)
      expect(loc.name).to eq("Tokyo")
    end

    it "未登録なら nil" do
      expect(directory.find("NLRTM")).to be_nil
    end
  end

  describe "#all" do
    it "登録済みの Location を UN/LOCODE 順で返す" do
      expect(directory.all.map(&:unlocode)).to eq(%w[JPTYO USLAX])
    end
  end
end
