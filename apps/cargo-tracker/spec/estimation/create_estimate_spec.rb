# frozen_string_literal: true

require "rails_helper"

# US01: 輸送見積作成のアプリ層〜永続化・BC 越境（Routing 候補・Billing 概算）の結合。
RSpec.describe "輸送見積作成（US01）" do
  subject(:estimation) { Estimation::Public::EstimationService.new }

  # 経路候補を返す Routing スタブ（外部 ACL を差し替え）。
  let(:routing_stub) do
    candidate = Struct.new(:voyage_numbers, :transit_days, :cost, :route_path, keyword_init: true).new(
      voyage_numbers: %w[V001], transit_days: 14, cost: 100_000, route_path: %w[JPTYO SGSIN USLAX]
    )
    result = Struct.new(:candidates, keyword_init: true) do
      def success? = candidates.present?
    end.new(candidates: [ candidate ])
    Class.new do
      define_method(:calculate_route_candidates) { |**_| result }
    end.new
  end

  def create(**overrides)
    estimation.create_estimate(
      { origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1),
        cargo_type: "GENERAL", weight_kg: 1000 }.merge(overrides),
      routing: routing_stub
    )
  end

  it "見積を作成すると見積番号が採番されルート候補と概算料金が付く（US01）" do
    result = create
    expect(result.status).to eq(:ok)
    expect(result.estimate_id).to match(/\A[0-9a-f-]{36}\z/)

    view = estimation.find(result.estimate_id)
    expect(view.candidates).not_to be_empty
    expect(view.candidates.first.voyage_number).to eq("V001")
    expect(view.candidates.first.transit_days).to eq(14)
    expect(view.candidates.first.estimated_cost).to be > 0 # FreightCalculator 概算
  end

  it "出発地と目的地が同一なら :invalid を返す" do
    expect(create(destination: "JPTYO").status).to eq(:invalid)
  end

  it "期限内に間に合うルートがない場合は :no_route を返す" do
    empty_routing = Class.new do
      def calculate_route_candidates(**_)
        Struct.new(:candidates) { def success? = false }.new([])
      end
    end.new
    result = estimation.create_estimate(
      { origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1),
        cargo_type: "GENERAL", weight_kg: 1000 }, routing: empty_routing
    )
    expect(result.status).to eq(:no_route)
  end
end
