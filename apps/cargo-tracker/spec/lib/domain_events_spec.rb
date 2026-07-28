# frozen_string_literal: true

require "rails_helper"

RSpec.describe DomainEvents do
  after { described_class.reset! }

  describe ".publish / .subscribe（ADR-0002）" do
    it "発行イベントを購読ハンドラへ同期で届ける" do
      received = []
      described_class.subscribe("cargo_routed") { |payload| received << payload }

      described_class.publish("cargo_routed", cargo_id: 7, tracking_number: "ABC123")

      expect(received.size).to eq(1)
      expect(received.first).to include(cargo_id: 7, tracking_number: "ABC123")
    end

    it "購読ハンドラの例外を発行側へ伝播させない" do
      described_class.subscribe("cargo_routed") { raise "handler boom" }

      expect do
        described_class.publish("cargo_routed", cargo_id: 1)
      end.not_to raise_error
    end

    it "別イベント名の購読者には配信しない" do
      received = []
      described_class.subscribe("cargo_confirmed") { |p| received << p }

      described_class.publish("cargo_routed", cargo_id: 1)

      expect(received).to be_empty
    end
  end
end
