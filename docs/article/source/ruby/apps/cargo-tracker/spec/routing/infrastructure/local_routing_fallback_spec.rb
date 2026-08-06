# frozen_string_literal: true

require "rails_helper"

# US08: 寄港地接続評価（多区間フォールバック）。
RSpec.describe Routing::Infrastructure::LocalRoutingFallback do
  subject(:fallback) { described_class.new }

  let(:request) do
    Struct.new(:origin, :destination, :arrival_deadline).new("JPTYO", "USLAX", Date.new(2026, 12, 1))
  end

  def register(number, origin, destination, dep, arr)
    Routing::Public::VoyageDirectory.new.register(
      voyage_number: number, carrier_name: "Line #{number}", supported_cargo_types: %w[GENERAL],
      movements: [ { departure_unlocode: origin, arrival_unlocode: destination,
                     departure_date: dep, arrival_date: arr, seq_number: 1 } ]
    )
  end

  it "直行便を最優先候補として返す" do
    register("V1", "JPTYO", "USLAX", "2026-09-01T09:00", "2026-09-15T18:00")
    candidates = fallback.candidates_for(request)
    expect(candidates.first.legs.size).to eq(1)
    expect(candidates.first.voyage_numbers).to eq([ "V1" ])
  end

  it "直行がなくても接続可能な 2 区間の乗り継ぎ候補を組み立てる" do
    register("V2", "JPTYO", "SGSIN", "2026-09-01T09:00", "2026-09-08T18:00")
    register("V3", "SGSIN", "USLAX", "2026-09-10T09:00", "2026-09-20T18:00") # V2 到着 ≤ V3 出発（接続可能）
    candidates = fallback.candidates_for(request)
    expect(candidates.size).to eq(1)
    expect(candidates.first.legs.size).to eq(2)
    expect(candidates.first.voyage_numbers).to eq(%w[V2 V3])
    expect(candidates.first.arrival_date).to eq(Time.zone.parse("2026-09-20T18:00"))
  end

  it "接続不能（前便到着が後便出発より後）の乗り継ぎは候補にしない" do
    register("V4", "JPTYO", "SGSIN", "2026-09-01T09:00", "2026-09-18T18:00")
    register("V5", "SGSIN", "USLAX", "2026-09-10T09:00", "2026-09-20T18:00") # V4 到着 > V5 出発
    expect(fallback.candidates_for(request)).to be_empty
  end

  it "直行と乗り継ぎが両方あれば直行を先頭にする" do
    register("V6", "JPTYO", "USLAX", "2026-09-01T09:00", "2026-09-16T18:00")
    register("V7", "JPTYO", "SGSIN", "2026-09-01T09:00", "2026-09-08T18:00")
    register("V8", "SGSIN", "USLAX", "2026-09-10T09:00", "2026-09-20T18:00")
    candidates = fallback.candidates_for(request)
    expect(candidates.first.legs.size).to eq(1)
    expect(candidates.map { |c| c.legs.size }).to eq([ 1, 2 ])
  end
end
