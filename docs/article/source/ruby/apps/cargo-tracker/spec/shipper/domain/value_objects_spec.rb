# frozen_string_literal: true

require "rails_helper"

RSpec.describe "Shipper Context 値オブジェクト" do
  describe Shipper::Domain::ShipperCode do
    it "SHP- プレフィックス付きコードを生成する" do
      code = described_class.generate
      expect(code.value).to match(/\ASHP-[0-9A-F]{8}\z/)
    end

    it "同値の 2 つは等価" do
      expect(described_class.new(value: "SHP-ABCD1234")).to eq(described_class.new(value: "SHP-ABCD1234"))
    end

    it "不正な形式は拒否する" do
      expect { described_class.new(value: "INVALID") }.to raise_error(ArgumentError)
    end
  end

  describe Shipper::Domain::ShipperType do
    it "INDIVIDUAL / CORPORATE を許可する" do
      expect(described_class.new(value: "INDIVIDUAL").individual?).to be true
      expect(described_class.new(value: "CORPORATE").corporate?).to be true
    end

    it "未知の種別は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end
  end

  describe Shipper::Domain::Address do
    it "500 文字までの住所を許可する" do
      expect(described_class.new(value: "a" * 500).value.length).to eq(500)
    end

    it "500 文字超は拒否する" do
      expect { described_class.new(value: "a" * 501) }.to raise_error(ArgumentError)
    end
  end

  describe Shipper::Domain::DiscountRate do
    it "0〜30% を許可する" do
      expect(described_class.new(value: "0.3").value).to eq(BigDecimal("0.3"))
      expect(described_class.new(value: "0").value).to eq(BigDecimal("0"))
    end

    it "30% 超は拒否する" do
      expect { described_class.new(value: "0.31") }.to raise_error(ArgumentError)
    end

    it "負値は拒否する" do
      expect { described_class.new(value: "-0.1") }.to raise_error(ArgumentError)
    end
  end
end
