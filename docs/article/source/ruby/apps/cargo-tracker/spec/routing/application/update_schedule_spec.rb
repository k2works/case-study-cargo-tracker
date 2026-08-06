# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Application::UpdateSchedule do
  subject(:use_case) { described_class.new(repository: repository) }

  let(:repository) { Routing::Infrastructure::ActiveRecordVoyageRepository.new }

  before do
    Routing::Application::RegisterVoyage.new(repository: repository).call(
      voyage_number: "V001", carrier_name: "Ocean Line", supported_cargo_types: %w[GENERAL],
      movements: [
        { departure_unlocode: "JPTYO", arrival_unlocode: "SGSIN",
          departure_date: "2026-11-01T09:00", arrival_date: "2026-11-06T18:00", seq_number: 1 }
      ]
    )
  end

  describe "#call（US25）" do
    let(:new_movements) do
      [
        { departure_unlocode: "JPOSA", arrival_unlocode: "USLAX",
          departure_date: "2026-12-01T09:00", arrival_date: "2026-12-15T18:00", seq_number: 1 }
      ]
    end

    it "既存航海のスケジュールを上書き更新する" do
      result = use_case.call(voyage_number: "V001", movements: new_movements)
      expect(result).to be_success

      reloaded = repository.find_by_number(Routing::Domain::VoyageNumber.new(value: "V001"))
      expect(reloaded.origin).to eq("JPOSA")
      expect(reloaded.destination).to eq("USLAX")
    end

    it "存在しない航海番号は失敗する" do
      result = use_case.call(voyage_number: "V999", movements: new_movements)
      expect(result).not_to be_success
      expect(result.error_message).to match(/存在しません/)
    end
  end
end
