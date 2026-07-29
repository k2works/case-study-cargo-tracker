# frozen_string_literal: true

require "rails_helper"

# IT7: 見積リポジトリの永続化（US01）。集約（PORO）と AR レコードの相互変換を検証する。
RSpec.describe Estimation::Infrastructure::ActiveRecordEstimateRepository do
  subject(:repository) { described_class.new }

  def build_estimate
    estimate = Estimation::Domain::Estimate.create(
      origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1),
      cargo_type: "GENERAL", weight_kg: BigDecimal("1000")
    )
    estimate.replace_candidates([
      Estimation::Domain::RouteCandidate.new(voyage_number: "V001", transit_port: "SGSIN",
                                             transit_days: 14, estimated_cost: BigDecimal("100000"), rank: 1),
      Estimation::Domain::RouteCandidate.new(voyage_number: "V002", transit_port: nil,
                                             transit_days: 18, estimated_cost: BigDecimal("90000"), rank: 2)
    ])
    estimate
  end

  it "見積と経路候補を保存して見積番号で取得できる" do
    estimate = build_estimate
    repository.save(estimate)

    found = repository.find_by_estimate_id(estimate.estimate_id)
    expect(found).not_to be_nil
    expect(found.origin).to eq("JPTYO")
    expect(found.destination).to eq("USLAX")
    expect(found.cargo_type).to eq("GENERAL")
    expect(found.weight_kg).to eq(BigDecimal("1000"))
    expect(found.status.value).to eq("CREATED")
    expect(found.candidates.map(&:voyage_number)).to eq(%w[V001 V002])
    expect(found.candidates.first.transit_port).to eq("SGSIN")
  end

  it "存在しない見積番号は nil を返す" do
    expect(repository.find_by_estimate_id("nonexistent")).to be_nil
  end

  it "全見積を取得できる" do
    repository.save(build_estimate)
    expect(repository.all.size).to eq(1)
  end
end
