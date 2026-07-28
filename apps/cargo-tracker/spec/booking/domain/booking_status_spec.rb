# frozen_string_literal: true

require "rails_helper"

RSpec.describe Booking::Domain::BookingStatus do
  it "9 つの状態値を持つ" do
    expect(described_class::VALUES).to contain_exactly(
      "PRELIMINARY", "ROUTE_REQUESTED", "ROUTE_PROPOSED", "CONFIRMED",
      "TRACKING_ISSUED", "IN_TRANSIT", "DELIVERED", "SETTLED", "CANCELLED"
    )
  end

  it "初期状態は PRELIMINARY を生成できる" do
    expect(described_class.initial.value).to eq("PRELIMINARY")
  end

  describe "#transition_to" do
    it "PRELIMINARY → ROUTE_REQUESTED は許可される（US06 引き渡し）" do
      status = described_class.new(value: "PRELIMINARY")
      expect(status.transition_to("ROUTE_REQUESTED").value).to eq("ROUTE_REQUESTED")
    end

    it "任意状態から CANCELLED へ遷移できる" do
      status = described_class.new(value: "ROUTE_PROPOSED")
      expect(status.transition_to("CANCELLED").value).to eq("CANCELLED")
    end

    it "PRELIMINARY → CONFIRMED のような不正遷移は例外" do
      status = described_class.new(value: "PRELIMINARY")
      expect { status.transition_to("CONFIRMED") }.to raise_error(described_class::InvalidTransition)
    end

    it "CANCELLED からは遷移できない" do
      status = described_class.new(value: "CANCELLED")
      expect { status.transition_to("PRELIMINARY") }.to raise_error(described_class::InvalidTransition)
    end
  end

  describe "述語" do
    it "#preliminary? / #route_requested? を判定できる" do
      expect(described_class.new(value: "PRELIMINARY").preliminary?).to be true
      expect(described_class.new(value: "ROUTE_REQUESTED").route_requested?).to be true
    end
  end
end
