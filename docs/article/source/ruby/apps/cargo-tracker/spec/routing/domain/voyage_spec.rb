# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Domain::Voyage do
  def movement(dep:, arr:, dep_date:, arr_date:, seq:)
    Routing::Domain::CarrierMovement.new(
      departure_unlocode: dep, arrival_unlocode: arr,
      departure_date: dep_date, arrival_date: arr_date, seq_number: seq
    )
  end

  let(:movements) do
    [
      movement(dep: "JPTYO", arr: "SGSIN", dep_date: Time.utc(2026, 11, 1, 9), arr_date: Time.utc(2026, 11, 6, 18), seq: 1),
      movement(dep: "SGSIN", arr: "NLRTM", dep_date: Time.utc(2026, 11, 8, 9), arr_date: Time.utc(2026, 11, 25, 18), seq: 2)
    ]
  end

  describe ".register（US24）" do
    subject(:voyage) do
      described_class.register(voyage_number: "V001", carrier_name: "Ocean Line", ship_name: "Pacific Star",
                               supported_cargo_types: %w[GENERAL REFRIGERATED], movements: movements)
    end

    it "航海番号・運送会社・スケジュールを保持する" do
      expect(voyage.voyage_number.value).to eq("V001")
      expect(voyage.carrier_name).to eq("Ocean Line")
      expect(voyage.schedule.movements.size).to eq(2)
    end

    it "出発地・到着地を取得できる" do
      expect(voyage.origin).to eq("JPTYO")
      expect(voyage.destination).to eq("NLRTM")
    end

    it "対応貨物種別を判定できる" do
      expect(voyage.supports?("GENERAL")).to be true
      expect(voyage.supports?("HAZARDOUS")).to be false
    end
  end

  describe "不変条件" do
    it "運送会社は必須" do
      expect do
        described_class.register(voyage_number: "V001", carrier_name: "", supported_cargo_types: %w[GENERAL], movements: movements)
      end.to raise_error(ArgumentError, /運送会社/)
    end

    it "スケジュールは 1 区間以上必要" do
      expect do
        described_class.register(voyage_number: "V001", carrier_name: "X", supported_cargo_types: %w[GENERAL], movements: [])
      end.to raise_error(ArgumentError, /区間/)
    end

    it "区間の出発日は到着日より前（日付整合。CarrierMovement で担保）" do
      expect do
        movement(dep: "JPTYO", arr: "SGSIN", dep_date: Time.utc(2026, 11, 10), arr_date: Time.utc(2026, 11, 1), seq: 1)
      end.to raise_error(ArgumentError, /出発日/)
    end

    it "区間は時系列で連結する（前区間の到着地=次区間の出発地）" do
      disconnected = [
        movement(dep: "JPTYO", arr: "SGSIN", dep_date: Time.utc(2026, 11, 1), arr_date: Time.utc(2026, 11, 6), seq: 1),
        movement(dep: "USLAX", arr: "NLRTM", dep_date: Time.utc(2026, 11, 8), arr_date: Time.utc(2026, 11, 20), seq: 2)
      ]
      expect do
        described_class.register(voyage_number: "V001", carrier_name: "X", supported_cargo_types: %w[GENERAL], movements: disconnected)
      end.to raise_error(ArgumentError, /連結|接続/)
    end

    it "後続区間が先行区間の到着より前に出発する場合は時系列エラー" do
      time_travel = [
        movement(dep: "JPTYO", arr: "SGSIN", dep_date: Time.utc(2026, 11, 10), arr_date: Time.utc(2026, 11, 20), seq: 1),
        movement(dep: "SGSIN", arr: "NLRTM", dep_date: Time.utc(2026, 11, 15), arr_date: Time.utc(2026, 11, 25), seq: 2)
      ]
      expect do
        described_class.register(voyage_number: "V001", carrier_name: "X", supported_cargo_types: %w[GENERAL], movements: time_travel)
      end.to raise_error(ArgumentError, /時系列/)
    end
  end

  describe "#update_schedule（US25）" do
    it "スケジュールを差し替えられる" do
      voyage = described_class.register(voyage_number: "V001", carrier_name: "Ocean Line",
                                        supported_cargo_types: %w[GENERAL], movements: movements)
      new_movements = [ movement(dep: "JPOSA", arr: "USLAX", dep_date: Time.utc(2026, 12, 1), arr_date: Time.utc(2026, 12, 15), seq: 1) ]
      voyage.update_schedule(new_movements)
      expect(voyage.origin).to eq("JPOSA")
      expect(voyage.destination).to eq("USLAX")
    end
  end
end
