# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shipper::Domain::Shipper do
  let(:base_attrs) do
    {
      code: Shipper::Domain::ShipperCode.new(value: "SHP-ABCD1234"),
      name: "山田太郎",
      email: "yamada@example.com",
      phone: "03-1234-5678",
      address: Shipper::Domain::Address.new(value: "東京都千代田区")
    }
  end

  describe "個人荷主" do
    subject(:shipper) do
      described_class.register(**base_attrs, shipper_type: Shipper::Domain::ShipperType.new(value: "INDIVIDUAL"))
    end

    it "個人種別で登録できる" do
      expect(shipper.shipper_type.individual?).to be true
    end

    it "法人ではない" do
      expect(shipper).not_to be_corporate
    end
  end

  describe "法人荷主（CorporateShipper）" do
    subject(:corporate) do
      Shipper::Domain::CorporateShipper.register(
        **base_attrs,
        contract_number: "C-0001",
        discount_rate: Shipper::Domain::DiscountRate.new(value: "0.15")
      )
    end

    it "契約番号と割引率を保持する" do
      expect(corporate.contract_number).to eq("C-0001")
      expect(corporate.discount_rate.percentage).to eq(15)
    end

    it "種別は CORPORATE" do
      expect(corporate.shipper_type.corporate?).to be true
      expect(corporate).to be_corporate
    end

    it "契約番号が nil なら登録できない" do
      expect do
        Shipper::Domain::CorporateShipper.register(
          **base_attrs, contract_number: nil,
          discount_rate: Shipper::Domain::DiscountRate.new(value: "0.15")
        )
      end.to raise_error(ArgumentError)
    end
  end

  describe "不変条件" do
    it "メールアドレスは必須" do
      expect do
        described_class.register(**base_attrs.merge(email: nil),
                                 shipper_type: Shipper::Domain::ShipperType.new(value: "INDIVIDUAL"))
      end.to raise_error(ArgumentError)
    end

    it "名称は必須" do
      expect do
        described_class.register(**base_attrs.merge(name: ""),
                                 shipper_type: Shipper::Domain::ShipperType.new(value: "INDIVIDUAL"))
      end.to raise_error(ArgumentError)
    end
  end
end
