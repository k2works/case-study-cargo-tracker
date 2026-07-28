# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Application::SearchVoyages do
  subject(:search) { described_class.new(repository: repository) }

  let(:repository) { Routing::Infrastructure::ActiveRecordVoyageRepository.new }

  def register(number:, origin:, dest:, dep:, arr:, types:)
    Routing::Application::RegisterVoyage.new(repository: repository).call(
      voyage_number: number, carrier_name: "Line #{number}", supported_cargo_types: types,
      movements: [ { departure_unlocode: origin, arrival_unlocode: dest,
                     departure_date: dep, arrival_date: arr, seq_number: 1 } ]
    )
  end

  before do
    register(number: "V001", origin: "JPTYO", dest: "USLAX", dep: "2026-11-01T09:00", arr: "2026-11-15T18:00", types: %w[GENERAL])
    register(number: "V002", origin: "JPTYO", dest: "USLAX", dep: "2026-12-01T09:00", arr: "2026-12-15T18:00", types: %w[GENERAL HAZARDOUS])
    register(number: "V003", origin: "JPOSA", dest: "NLRTM", dep: "2026-11-05T09:00", arr: "2026-11-28T18:00", types: %w[GENERAL REFRIGERATED])
  end

  describe "#call（US07）" do
    it "出発地・目的地で絞り込む" do
      results = search.call(origin: "JPTYO", destination: "USLAX")
      expect(results.map(&:voyage_number)).to contain_exactly("V001", "V002")
    end

    it "出発期間で絞り込む" do
      results = search.call(origin: "JPTYO", destination: "USLAX", departure_from: "2026-11-15", departure_to: "2026-12-31")
      expect(results.map(&:voyage_number)).to contain_exactly("V002")
    end

    it "貨物種別で対応可能な航海のみに絞り込む（危険物）" do
      results = search.call(origin: "JPTYO", destination: "USLAX", cargo_type: "HAZARDOUS")
      expect(results.map(&:voyage_number)).to contain_exactly("V002")
    end

    it "冷凍貨物は対応航海のみ" do
      results = search.call(origin: "JPOSA", destination: "NLRTM", cargo_type: "REFRIGERATED")
      expect(results.map(&:voyage_number)).to contain_exactly("V003")
    end

    it "条件を満たす航海がない場合は空を返す" do
      expect(search.call(origin: "JPTYO", destination: "SGSIN")).to be_empty
    end

    it "結果に航海番号・運送会社・出発/到着日・経由港が含まれる" do
      result = search.call(origin: "JPTYO", destination: "USLAX").first
      expect(result.carrier_name).to be_present
      expect(result.ports).to include("JPTYO", "USLAX")
      expect(result.departure_date).to be_present
    end
  end
end
