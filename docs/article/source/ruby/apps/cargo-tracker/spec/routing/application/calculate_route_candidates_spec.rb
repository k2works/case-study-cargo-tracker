# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Application::CalculateRouteCandidates do
  subject(:use_case) { described_class.new(routing_service: routing_service) }

  let(:routing_service) { instance_double(Routing::Domain::ExternalCargoRoutingService) }

  def candidate(voyages:, days:, cost:, from: "JPTYO", to: "USLAX", fallback: false)
    legs = voyages.each_cons(2).map { |a, b| { from: a[:port], to: b[:port], voyage_number: b[:vn] } }
    legs = [ { from: from, to: to, voyage_number: voyages.first[:vn] } ] if voyages.size == 1
    Routing::Domain::RouteCandidate.new(legs: legs, transit_days: days, cost: cost,
                                        voyage_numbers: voyages.map { |v| v[:vn] }, fallback: fallback)
  end

  let(:request_args) { { origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-12-01" } }

  describe "#call（US08）" do
    context "候補が返る" do
      before do
        allow(routing_service).to receive(:search_routes).and_return([
          candidate(voyages: [ { vn: "V010", port: "JPTYO" }, { vn: "V011", port: "SGSIN" } ], days: 20, cost: 90_000),
          candidate(voyages: [ { vn: "V001" } ], days: 14, cost: 120_000) # 直行
        ])
      end

      it "経路候補を推奨順（直行便優先→所要日数昇順）で返す" do
        result = use_case.call(**request_args)
        expect(result).to be_success
        expect(result.candidates.first.direct?).to be true # 直行便が最優先
        expect(result.candidates.map(&:transit_days)).to eq([ 14, 20 ])
      end

      it "各候補に所要日数・費用・航海番号が含まれる" do
        c = use_case.call(**request_args).candidates.first
        expect(c.transit_days).to eq(14)
        expect(c.voyage_numbers).to eq(%w[V001])
      end
    end

    context "期限内に到達可能な経路がない" do
      before do
        # 到着期限 2026-12-01 に対し所要 200 日（出発を今日として超過）
        allow(routing_service).to receive(:search_routes).and_return([
          candidate(voyages: [ { vn: "V999" } ], days: 400, cost: 100_000)
        ])
      end

      it "期限超過を通知し条件調整を促す" do
        result = use_case.call(origin: "JPTYO", destination: "USLAX", arrival_deadline: "2026-08-01", departure_date: "2026-07-28")
        expect(result).not_to be_success
        expect(result.message).to match(/期限内に到達可能な経路がありません/)
      end
    end

    context "候補が空" do
      before { allow(routing_service).to receive(:search_routes).and_return([]) }

      it "候補なしを通知する" do
        result = use_case.call(**request_args)
        expect(result).not_to be_success
        expect(result.message).to match(/経路/)
      end
    end
  end
end
