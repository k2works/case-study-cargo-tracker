# frozen_string_literal: true

require "rails_helper"

RSpec.describe "Booking Context 値オブジェクト・enum" do
  describe Booking::Domain::BookingId do
    it "BKG- プレフィックス付き予約番号を生成する" do
      expect(described_class.generate.value).to match(/\ABKG-[0-9A-F]{8}\z/)
    end

    it "不正形式は拒否する" do
      expect { described_class.new(value: "X") }.to raise_error(ArgumentError)
    end
  end

  describe Booking::Domain::CargoType do
    it "GENERAL/HAZARDOUS/REFRIGERATED を許可する" do
      expect(described_class.new(value: "GENERAL").general?).to be true
      expect(described_class.new(value: "HAZARDOUS").hazardous?).to be true
      expect(described_class.new(value: "REFRIGERATED").refrigerated?).to be true
    end

    it "未知の種別は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end
  end

  describe Booking::Domain::RouteSpecification do
    it "出発地・目的地・到着期限を保持する" do
      spec = described_class.new(origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1))
      expect(spec.origin).to eq("JPOSA")
      expect(spec.destination).to eq("USLAX")
    end

    it "出発地と目的地が同一なら拒否する" do
      expect do
        described_class.new(origin: "JPOSA", destination: "JPOSA", arrival_deadline: Date.new(2026, 12, 1))
      end.to raise_error(ArgumentError)
    end
  end

  describe Booking::Domain::HazardousDeclaration do
    it "危険物クラス・UN 番号・正式輸送品名を保持する" do
      d = described_class.new(hazardous_class: "3", un_number: "UN1203", proper_shipping_name: "GASOLINE")
      expect(d.un_number).to eq("UN1203")
    end

    it "危険物クラスが空なら拒否する" do
      expect do
        described_class.new(hazardous_class: "", un_number: "UN1203", proper_shipping_name: "X")
      end.to raise_error(ArgumentError)
    end
  end

  describe Booking::Domain::TemperatureRequirement do
    it "最低/最高温度・単位を保持する" do
      t = described_class.new(min_temperature: -20, max_temperature: -10, unit: "CELSIUS")
      expect(t.unit).to eq("CELSIUS")
    end

    it "最低温度が最高温度を上回るなら拒否する" do
      expect do
        described_class.new(min_temperature: 5, max_temperature: -5, unit: "CELSIUS")
      end.to raise_error(ArgumentError)
    end

    it "未知の温度単位は拒否する" do
      expect do
        described_class.new(min_temperature: -20, max_temperature: -10, unit: "KELVIN")
      end.to raise_error(ArgumentError)
    end
  end
end
