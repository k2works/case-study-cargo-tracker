# frozen_string_literal: true

require "rails_helper"

RSpec.describe Booking::Domain::Cargo do
  def route
    Booking::Domain::RouteSpecification.new(origin: "JPOSA", destination: "USLAX",
                                            arrival_deadline: Date.new(2026, 12, 1))
  end

  def base_attrs(**overrides)
    {
      shipper_id: 1,
      cargo_type: Booking::Domain::CargoType.new(value: "GENERAL"),
      weight_kg: 1200.5,
      route_specification: route
    }.merge(overrides)
  end

  describe ".book（US04）" do
    subject(:cargo) { described_class.book(**base_attrs) }

    it "予約番号が発行され、状態が PRELIMINARY になる" do
      expect(cargo.booking_id.value).to match(/\ABKG-/)
      expect(cargo.booking_status.preliminary?).to be true
    end

    it "荷主 ID・貨物種別・重量・ルート仕様を保持する" do
      expect(cargo.shipper_id).to eq(1)
      expect(cargo.cargo_type.general?).to be true
      expect(cargo.route_specification.destination).to eq("USLAX")
    end

    it "重量が 0 以下なら登録できない" do
      expect { described_class.book(**base_attrs(weight_kg: 0)) }.to raise_error(ArgumentError)
    end

    it "荷主 ID が未指定なら登録できない" do
      expect { described_class.book(**base_attrs(shipper_id: nil)) }.to raise_error(ArgumentError)
    end
  end

  describe "危険物・冷凍の条件付き必須（US05）" do
    it "HAZARDOUS なら危険物申告が必須（nil で例外）" do
      expect do
        described_class.book(**base_attrs(cargo_type: Booking::Domain::CargoType.new(value: "HAZARDOUS")))
      end.to raise_error(ArgumentError, /危険物申告/)
    end

    it "HAZARDOUS + 危険物申告ありで登録できる" do
      cargo = described_class.book(**base_attrs(
        cargo_type: Booking::Domain::CargoType.new(value: "HAZARDOUS"),
        hazardous_declaration: Booking::Domain::HazardousDeclaration.new(
          hazardous_class: "3", un_number: "UN1203", proper_shipping_name: "GASOLINE"
        )
      ))
      expect(cargo.hazardous_declaration.un_number).to eq("UN1203")
    end

    it "REFRIGERATED なら温度条件が必須（nil で例外）" do
      expect do
        described_class.book(**base_attrs(cargo_type: Booking::Domain::CargoType.new(value: "REFRIGERATED")))
      end.to raise_error(ArgumentError, /温度/)
    end

    it "REFRIGERATED + 温度条件ありで登録できる" do
      cargo = described_class.book(**base_attrs(
        cargo_type: Booking::Domain::CargoType.new(value: "REFRIGERATED"),
        temperature_requirement: Booking::Domain::TemperatureRequirement.new(
          min_temperature: -20, max_temperature: -10, unit: "CELSIUS"
        )
      ))
      expect(cargo.temperature_requirement.unit).to eq("CELSIUS")
    end
  end

  describe "#assign_to_routing（US06）" do
    subject(:cargo) { described_class.book(**base_attrs) }

    it "PRELIMINARY から ROUTE_REQUESTED へ遷移する" do
      cargo.assign_to_routing
      expect(cargo.booking_status.route_requested?).to be true
    end

    it "PRELIMINARY 以外からの引き渡しは不正遷移で例外" do
      cargo.assign_to_routing
      expect { cargo.assign_to_routing }.to raise_error(Booking::Domain::BookingStatus::InvalidTransition)
    end
  end

  describe "#assign_itinerary（US09/US11）" do
    subject(:cargo) do
      c = described_class.book(**base_attrs)
      c.assign_to_routing
      c
    end

    def leg(load:, unload:, voyage: "V001", load_time: nil, unload_time: nil)
      Booking::Domain::Leg.new(load_location: load, unload_location: unload, voyage_number: voyage,
                               load_time: load_time || Time.utc(2026, 9, 1, 8),
                               unload_time: unload_time || Time.utc(2026, 11, 20, 18))
    end

    def valid_itinerary
      Booking::Domain::CargoItinerary.new(legs: [ leg(load: "JPOSA", unload: "USLAX") ])
    end

    it "ROUTE_REQUESTED から旅程を紐付けると ROUTE_PROPOSED になる" do
      cargo.assign_itinerary(valid_itinerary)
      expect(cargo.booking_status.route_proposed?).to be true
      expect(cargo.cargo_itinerary.destination).to eq("USLAX")
    end

    it "ルート仕様を満たさない旅程（出発地不一致）は InvalidItineraryError" do
      itinerary = Booking::Domain::CargoItinerary.new(legs: [ leg(load: "JPTYO", unload: "USLAX") ])
      expect { cargo.assign_itinerary(itinerary) }
        .to raise_error(Booking::Domain::Cargo::InvalidItineraryError)
    end

    it "到着予定が期限を超過する旅程は InvalidItineraryError" do
      late = Booking::Domain::CargoItinerary.new(legs: [
        leg(load: "JPOSA", unload: "USLAX", unload_time: Time.utc(2026, 12, 20, 18))
      ])
      expect { cargo.assign_itinerary(late) }
        .to raise_error(Booking::Domain::Cargo::InvalidItineraryError)
    end
  end

  describe "予約確定・差戻し・キャンセル（US13）" do
    subject(:cargo) do
      c = described_class.book(**base_attrs)
      c.assign_to_routing
      c.assign_itinerary(Booking::Domain::CargoItinerary.new(legs: [
        Booking::Domain::Leg.new(load_location: "JPOSA", unload_location: "USLAX", voyage_number: "V001",
                                 load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 11, 20, 18))
      ]))
      c
    end

    it "#confirm で ROUTE_PROPOSED から CONFIRMED になる" do
      cargo.confirm
      expect(cargo.booking_status.confirmed?).to be true
    end

    it "#back_to_routing で ROUTE_PROPOSED から ROUTE_REQUESTED へ差戻す" do
      cargo.back_to_routing
      expect(cargo.booking_status.route_requested?).to be true
    end

    it "#cancel で CANCELLED になる" do
      cargo.cancel
      expect(cargo.booking_status.cancelled?).to be true
    end

    it "ROUTE_PROPOSED 以外からの確定は不正遷移で例外" do
      cargo.confirm
      expect { cargo.confirm }.to raise_error(Booking::Domain::BookingStatus::InvalidTransition)
    end

    it "ROUTE_PROPOSED 以外からの差戻しは不正遷移で例外" do
      cargo.confirm
      expect { cargo.back_to_routing }.to raise_error(Booking::Domain::BookingStatus::InvalidTransition)
    end
  end
end
