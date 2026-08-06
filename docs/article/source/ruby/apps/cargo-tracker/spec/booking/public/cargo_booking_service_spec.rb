# frozen_string_literal: true

require "rails_helper"

RSpec.describe Booking::Public::CargoBookingService do
  let(:checker) { instance_double(Booking::Domain::ShipperExistenceChecker, exists?: true) }
  let(:location_checker) { instance_double(Booking::Domain::LocationExistenceChecker, exists?: true) }
  let(:notifier) { instance_spy(Booking::Domain::RoutingNotifier) }
  subject(:service) do
    described_class.new(shipper_existence_checker: checker, location_existence_checker: location_checker,
                        notifier: notifier)
  end

  let(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "荷主太郎", email: "n@example.com", address: "東京"
    ).shipper_id
  end

  def book
    service.book(shipper_id: shipper_id, cargo_type: "GENERAL", weight_kg: "10",
                 origin: "JPOSA", destination: "USLAX", arrival_deadline: "2026-12-01")
  end

  describe "#assign_to_routing" do
    it "存在する予約を引き渡すと :ok を返し、経路設計依頼通知を送る（US06）" do
      booking_id = book.booking_id
      expect(service.assign_to_routing(booking_id)).to eq(:ok)
      expect(notifier).to have_received(:notify_routing_requested).with(booking_id)
    end

    it "存在しない予約番号は :not_found" do
      expect(service.assign_to_routing("BKG-DEADBEEF")).to eq(:not_found)
    end

    it "不正な形式の予約番号は :not_found" do
      expect(service.assign_to_routing("invalid")).to eq(:not_found)
    end

    it "既に引き渡し済み（ROUTE_REQUESTED）を再引き渡しすると :invalid" do
      booking_id = book.booking_id
      service.assign_to_routing(booking_id)
      expect(service.assign_to_routing(booking_id)).to eq(:invalid)
    end
  end

  describe "#find" do
    it "不正形式の予約番号は nil（500 にしない）" do
      expect(service.find("invalid")).to be_nil
    end
  end
end
