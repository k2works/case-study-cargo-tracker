# frozen_string_literal: true

require "rails_helper"

RSpec.describe Booking::Infrastructure::ActiveRecordCargoRepository do
  subject(:repository) { described_class.new }

  let(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "山田太郎", email: "yamada@example.com", address: "東京"
    ).shipper_id
  end

  def build_general
    Booking::Domain::Cargo.book(
      shipper_id: shipper_id,
      cargo_type: Booking::Domain::CargoType.new(value: "GENERAL"),
      weight_kg: 1200.5,
      route_specification: Booking::Domain::RouteSpecification.new(
        origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1)
      ),
      description: "自動車部品"
    )
  end

  def build_hazardous
    Booking::Domain::Cargo.book(
      shipper_id: shipper_id,
      cargo_type: Booking::Domain::CargoType.new(value: "HAZARDOUS"),
      weight_kg: 500,
      route_specification: Booking::Domain::RouteSpecification.new(
        origin: "JPOSA", destination: "USLAX", arrival_deadline: Date.new(2026, 12, 1)
      ),
      hazardous_declaration: Booking::Domain::HazardousDeclaration.new(
        hazardous_class: "3", un_number: "UN1203", proper_shipping_name: "GASOLINE"
      )
    )
  end

  describe "#save / #find_by_booking_id" do
    it "一般貨物を永続化し予約番号で復元できる" do
      cargo = build_general
      repository.save(cargo)

      reloaded = repository.find_by_booking_id(cargo.booking_id)
      expect(reloaded.cargo_type.general?).to be true
      expect(reloaded.booking_status.preliminary?).to be true
      expect(reloaded.route_specification.destination).to eq("USLAX")
      expect(reloaded.description).to eq("自動車部品")
    end

    it "危険物貨物を危険物申告とともに永続化・復元できる" do
      cargo = build_hazardous
      repository.save(cargo)

      reloaded = repository.find_by_booking_id(cargo.booking_id)
      expect(reloaded.cargo_type.hazardous?).to be true
      expect(reloaded.hazardous_declaration.un_number).to eq("UN1203")
    end

    it "状態遷移を永続化できる（引き渡し後 ROUTE_REQUESTED）" do
      cargo = build_general
      repository.save(cargo)
      cargo.assign_to_routing
      repository.save(cargo)

      expect(repository.find_by_booking_id(cargo.booking_id).booking_status.route_requested?).to be true
    end
  end

  describe "#all" do
    it "登録済み貨物を返す" do
      repository.save(build_general)
      expect(repository.all.size).to eq(1)
    end
  end
end
