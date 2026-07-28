# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Application::RegisterVoyage do
  subject(:use_case) { described_class.new(repository: repository) }

  let(:repository) { Routing::Infrastructure::ActiveRecordVoyageRepository.new }

  let(:params) do
    {
      voyage_number: "V001", carrier_name: "Ocean Line", ship_name: "Pacific Star",
      supported_cargo_types: %w[GENERAL REFRIGERATED],
      movements: [
        { departure_unlocode: "JPTYO", arrival_unlocode: "SGSIN",
          departure_date: "2026-11-01T09:00", arrival_date: "2026-11-06T18:00", seq_number: 1 },
        { departure_unlocode: "SGSIN", arrival_unlocode: "NLRTM",
          departure_date: "2026-11-08T09:00", arrival_date: "2026-11-25T18:00", seq_number: 2 }
      ]
    }
  end

  describe "#call（US24）" do
    it "航海を登録し航海番号を返す" do
      result = use_case.call(**params)
      expect(result).to be_success
      expect(result.voyage_number).to eq("V001")
      expect(repository.exists_by_number?("V001")).to be true
    end

    it "同一航海番号が既に存在する場合は登録に失敗する" do
      use_case.call(**params)
      result = use_case.call(**params)
      expect(result).not_to be_success
      expect(result.error_message).to match(/航海番号/)
    end

    it "運送会社が空だと日本語メッセージで失敗する（内部例外を露出しない）" do
      result = use_case.call(**params.merge(carrier_name: ""))
      expect(result).not_to be_success
      expect(result.error_message).to match(/運送会社/)
    end

    it "出発日が到着日より後の区間は日付整合エラーになる" do
      bad = params[:movements].dup
      bad[0] = bad[0].merge(departure_date: "2026-11-10T00:00", arrival_date: "2026-11-01T00:00")
      result = use_case.call(**params.merge(movements: bad))
      expect(result).not_to be_success
      expect(result.error_message).to match(/出発日/)
    end
  end
end
