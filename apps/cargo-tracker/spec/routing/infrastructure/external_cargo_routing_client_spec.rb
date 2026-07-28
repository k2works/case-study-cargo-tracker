# frozen_string_literal: true

require "rails_helper"

RSpec.describe Routing::Infrastructure::ExternalCargoRoutingClient do
  subject(:client) { described_class.new(base_url: "http://routing.example.com", fallback: fallback) }

  let(:fallback) { instance_double(Routing::Infrastructure::LocalRoutingFallback) }
  let(:request) do
    Routing::Domain::ExternalCargoRoutingService::SearchRequest.new(
      origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1)
    )
  end

  describe "#search_routes（WebMock 契約）" do
    context "正常系: 外部システムが 3 候補を返す" do
      before do
        body = {
          candidates: [
            { legs: [ { from: "JPTYO", to: "USLAX", voyage_number: "V001" } ], transit_days: 14, cost: 120_000 },
            { legs: [ { from: "JPTYO", to: "SGSIN", voyage_number: "V010" }, { from: "SGSIN", to: "USLAX", voyage_number: "V011" } ], transit_days: 20, cost: 90_000 },
            { legs: [ { from: "JPTYO", to: "USLAX", voyage_number: "V002" } ], transit_days: 16, cost: 110_000 }
          ]
        }.to_json
        stub_request(:post, "http://routing.example.com/search")
          .to_return(status: 200, body: body, headers: { "Content-Type" => "application/json" })
      end

      it "3 件の RouteCandidate を返す" do
        candidates = client.search_routes(request)
        expect(candidates.size).to eq(3)
        expect(candidates.first.voyage_numbers).to eq(%w[V001])
        expect(candidates).to all(be_a(Routing::Domain::RouteCandidate))
      end

      it "フォールバックには委譲しない" do
        expect(fallback).not_to receive(:candidates_for)
        client.search_routes(request)
      end
    end

    context "正常系: transit_days が文字列で返る" do
      before do
        body = { candidates: [ { legs: [ { from: "JPTYO", to: "USLAX", voyage_number: "V001" } ], transit_days: "14", cost: 120_000 } ] }.to_json
        stub_request(:post, "http://routing.example.com/search")
          .to_return(status: 200, body: body, headers: { "Content-Type" => "application/json" })
      end

      it "所要日数を整数に正規化する（期限計算の破綻を防ぐ）" do
        candidates = client.search_routes(request)
        expect(candidates.first.transit_days).to eq(14)
      end
    end

    context "異常系: 5xx サーバエラー" do
      before do
        stub_request(:post, "http://routing.example.com/search").to_return(status: 503, body: "error")
        allow(fallback).to receive(:candidates_for).and_return([])
      end

      it "フォールバックに委譲する" do
        client.search_routes(request)
        expect(fallback).to have_received(:candidates_for).with(request)
      end
    end

    context "異常系: 不正な JSON" do
      before do
        stub_request(:post, "http://routing.example.com/search")
          .to_return(status: 200, body: "not-json", headers: { "Content-Type" => "application/json" })
        allow(fallback).to receive(:candidates_for).and_return([])
      end

      it "フォールバックに委譲する" do
        client.search_routes(request)
        expect(fallback).to have_received(:candidates_for).with(request)
      end
    end

    context "異常系: 接続タイムアウト" do
      before do
        stub_request(:post, "http://routing.example.com/search").to_timeout
        allow(fallback).to receive(:candidates_for).with(request).and_return(
          [ Routing::Domain::RouteCandidate.new(legs: [ { from: "JPTYO", to: "USLAX", voyage_number: "V999" } ],
                                                transit_days: 18, cost: 130_000, voyage_numbers: %w[V999], fallback: true) ]
        )
      end

      it "過去実績データからフォールバック候補を返す" do
        candidates = client.search_routes(request)
        expect(candidates.size).to eq(1)
        expect(candidates.first).to be_fallback
        expect(fallback).to have_received(:candidates_for).with(request)
      end
    end
  end
end
