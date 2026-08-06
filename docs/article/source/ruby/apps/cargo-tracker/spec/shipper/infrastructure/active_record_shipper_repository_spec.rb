# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shipper::Infrastructure::ActiveRecordShipperRepository do
  subject(:repository) { described_class.new }

  def build_individual(email: "yamada@example.com")
    Shipper::Domain::Shipper.register(
      code: Shipper::Domain::ShipperCode.generate,
      name: "山田太郎",
      email: email,
      phone: "03-1234-5678",
      address: Shipper::Domain::Address.new(value: "東京都千代田区"),
      shipper_type: Shipper::Domain::ShipperType.new(value: "INDIVIDUAL")
    )
  end

  def build_corporate(email: "corp@example.com")
    Shipper::Domain::CorporateShipper.register(
      code: Shipper::Domain::ShipperCode.generate,
      name: "株式会社サンプル",
      email: email,
      address: Shipper::Domain::Address.new(value: "大阪府大阪市"),
      contract_number: "C-0001",
      discount_rate: Shipper::Domain::DiscountRate.new(value: "0.15")
    )
  end

  describe "#save" do
    it "個人荷主を永続化し、コードで復元できる" do
      shipper = build_individual
      repository.save(shipper)

      reloaded = repository.find_by_code(shipper.code)
      expect(reloaded.name).to eq("山田太郎")
      expect(reloaded.shipper_type.individual?).to be true
      expect(reloaded).not_to be_corporate
    end

    it "法人荷主を契約番号・割引率とともに永続化する" do
      corporate = build_corporate
      repository.save(corporate)

      reloaded = repository.find_by_code(corporate.code)
      expect(reloaded).to be_corporate
      expect(reloaded.contract_number).to eq("C-0001")
      expect(reloaded.discount_rate.percentage).to eq(15)
    end
  end

  describe "#find_by_email" do
    it "登録済みメールで既存荷主を取得できる" do
      repository.save(build_individual(email: "dup@example.com"))
      expect(repository.find_by_email("dup@example.com")).not_to be_nil
    end

    it "未登録メールでは nil" do
      expect(repository.find_by_email("none@example.com")).to be_nil
    end
  end

  describe "#exists_by_email?" do
    it "登録済みなら true" do
      repository.save(build_individual(email: "dup@example.com"))
      expect(repository.exists_by_email?("dup@example.com")).to be true
    end
  end
end
