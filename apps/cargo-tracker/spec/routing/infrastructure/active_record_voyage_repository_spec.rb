# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Infrastructure::ActiveRecordVoyageRepository do
  subject(:repository) { described_class.new }

  def movement(dep:, arr:, dep_date:, arr_date:, seq:)
    Routing::Domain::CarrierMovement.new(
      departure_unlocode: dep, arrival_unlocode: arr,
      departure_date: dep_date, arrival_date: arr_date, seq_number: seq
    )
  end

  def build_voyage(number: "V001")
    Routing::Domain::Voyage.register(
      voyage_number: number, carrier_name: "Ocean Line", ship_name: "Pacific Star",
      supported_cargo_types: %w[GENERAL REFRIGERATED],
      movements: [
        movement(dep: "JPTYO", arr: "SGSIN", dep_date: Time.utc(2026, 11, 1, 9), arr_date: Time.utc(2026, 11, 6, 18), seq: 1),
        movement(dep: "SGSIN", arr: "NLRTM", dep_date: Time.utc(2026, 11, 8, 9), arr_date: Time.utc(2026, 11, 25, 18), seq: 2)
      ]
    )
  end

  describe "#save / #find_by_number" do
    it "航海とスケジュールを永続化し復元できる" do
      repository.save(build_voyage)
      reloaded = repository.find_by_number(Routing::Domain::VoyageNumber.new(value: "V001"))
      expect(reloaded.carrier_name).to eq("Ocean Line")
      expect(reloaded.origin).to eq("JPTYO")
      expect(reloaded.destination).to eq("NLRTM")
      expect(reloaded.schedule.movements.size).to eq(2)
      expect(reloaded.supports?("REFRIGERATED")).to be true
    end

    it "更新時はスケジュールを差し替える（US25）" do
      voyage = build_voyage
      repository.save(voyage)
      voyage.update_schedule([ movement(dep: "JPOSA", arr: "USLAX", dep_date: Time.utc(2026, 12, 1), arr_date: Time.utc(2026, 12, 15), seq: 1) ])
      repository.save(voyage)

      reloaded = repository.find_by_number(Routing::Domain::VoyageNumber.new(value: "V001"))
      expect(reloaded.origin).to eq("JPOSA")
      expect(reloaded.schedule.movements.size).to eq(1)
    end
  end

  describe "#exists_by_number?" do
    it "登録済み航海番号なら true" do
      repository.save(build_voyage)
      expect(repository.exists_by_number?("V001")).to be true
    end
  end

  describe "#all" do
    it "登録済み航海を返す" do
      repository.save(build_voyage(number: "V001"))
      repository.save(build_voyage(number: "V002"))
      expect(repository.all.map { |v| v.voyage_number.value }).to contain_exactly("V001", "V002")
    end
  end
end
