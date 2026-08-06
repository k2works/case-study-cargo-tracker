# frozen_string_literal: true

require "rails_helper"

# IT7: 見積集約のドメイン層（US01 輸送見積作成）
RSpec.describe "Estimation Context 見積集約" do
  describe Estimation::Domain::EstimateStatus do
    it "CREATED/EXPIRED を許可する" do
      %w[CREATED EXPIRED].each { |v| expect(described_class.new(value: v).value).to eq(v) }
    end

    it "未知の状態は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end

    it "初期状態は CREATED" do
      expect(described_class.initial.value).to eq("CREATED")
    end
  end

  describe Estimation::Domain::RouteCandidate do
    it "経由港・所要日数・概算料金・航海番号を保持する" do
      c = described_class.new(voyage_number: "V001", transit_port: "SGSIN", transit_days: 14,
                              estimated_cost: BigDecimal("100000"), rank: 1)
      expect(c.voyage_number).to eq("V001")
      expect(c.transit_days).to eq(14)
    end

    it "航海番号は必須・所要日数と料金は正である" do
      expect { described_class.new(voyage_number: "", transit_port: nil, transit_days: 1,
                                   estimated_cost: BigDecimal("1"), rank: 1) }.to raise_error(ArgumentError)
      expect { described_class.new(voyage_number: "V1", transit_port: nil, transit_days: 0,
                                   estimated_cost: BigDecimal("1"), rank: 1) }.to raise_error(ArgumentError)
      expect { described_class.new(voyage_number: "V1", transit_port: nil, transit_days: 1,
                                   estimated_cost: BigDecimal("0"), rank: 1) }.to raise_error(ArgumentError)
    end
  end

  describe Estimation::Domain::Estimate do
    def valid_args(**overrides)
      { origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1),
        cargo_type: "GENERAL", weight_kg: BigDecimal("1000") }.merge(overrides)
    end

    it "見積を作成すると見積番号が採番され CREATED になる（US01）" do
      estimate = Estimation::Domain::Estimate.create(**valid_args)
      expect(estimate.estimate_id).to match(/\A[0-9a-f-]{36}\z/) # UUID
      expect(estimate.status.value).to eq("CREATED")
      expect(estimate.candidates).to eq([])
    end

    it "出発地と目的地が同一の見積は拒否する" do
      expect { Estimation::Domain::Estimate.create(**valid_args(destination: "JPTYO")) }
        .to raise_error(ArgumentError, /出発地と目的地/)
    end

    it "重量は正である" do
      expect { Estimation::Domain::Estimate.create(**valid_args(weight_kg: BigDecimal("0"))) }
        .to raise_error(ArgumentError, /重量/)
    end

    it "ルート候補を一括で差し替えできる（US01 候補算出）" do
      estimate = Estimation::Domain::Estimate.create(**valid_args)
      candidates = [
        Estimation::Domain::RouteCandidate.new(voyage_number: "V001", transit_port: "SGSIN",
                                               transit_days: 14, estimated_cost: BigDecimal("100000"), rank: 1)
      ]
      estimate.replace_candidates(candidates)
      expect(estimate.candidates.size).to eq(1)
      expect(estimate.candidates.first.voyage_number).to eq("V001")
    end
  end
end
