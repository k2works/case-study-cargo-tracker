# frozen_string_literal: true

require "rails_helper"

RSpec.describe "Booking Context 旅程（CargoItinerary/Leg）" do
  def leg(load:, unload:, voyage: "V001", load_time: nil, unload_time: nil)
    Booking::Domain::Leg.new(
      load_location: load, unload_location: unload, voyage_number: voyage,
      load_time: load_time || Time.utc(2026, 9, 1, 8), unload_time: unload_time || Time.utc(2026, 9, 10, 18)
    )
  end

  describe Booking::Domain::Leg do
    it "積地・揚地・航海番号・積揚時刻を保持する" do
      l = leg(load: "JPTYO", unload: "USLAX")
      expect(l.load_location).to eq("JPTYO")
      expect(l.unload_location).to eq("USLAX")
      expect(l.voyage_number).to eq("V001")
    end

    it "積地と揚地が同一なら拒否する" do
      expect { leg(load: "JPTYO", unload: "JPTYO") }.to raise_error(ArgumentError)
    end

    it "積地が UN/LOCODE 形式でなければ拒否する" do
      expect { leg(load: "abc", unload: "USLAX") }.to raise_error(ArgumentError)
    end

    it "航海番号が空なら拒否する" do
      expect { leg(load: "JPTYO", unload: "USLAX", voyage: "") }.to raise_error(ArgumentError)
    end
  end

  describe Booking::Domain::CargoItinerary do
    it "1 区間以上の脚を保持し出発地・目的地・到着時刻を導出する" do
      itinerary = described_class.new(legs: [
        leg(load: "JPTYO", unload: "SGSIN", load_time: Time.utc(2026, 9, 1, 8), unload_time: Time.utc(2026, 9, 8, 12)),
        leg(load: "SGSIN", unload: "USLAX", load_time: Time.utc(2026, 9, 9, 8), unload_time: Time.utc(2026, 9, 20, 18))
      ])
      expect(itinerary.origin).to eq("JPTYO")
      expect(itinerary.destination).to eq("USLAX")
      expect(itinerary.expected_arrival_time).to eq(Time.utc(2026, 9, 20, 18))
    end

    it "脚が空なら拒否する" do
      expect { described_class.new(legs: []) }.to raise_error(ArgumentError)
    end

    it "隣接脚が連結しない（Leg[n].unload != Leg[n+1].load）なら拒否する" do
      expect do
        described_class.new(legs: [
          leg(load: "JPTYO", unload: "SGSIN"),
          leg(load: "CNSHA", unload: "USLAX")
        ])
      end.to raise_error(ArgumentError)
    end

    it "最終脚の荷揚時刻が nil なら拒否する（到着期限判定の前提）" do
      leg_without_unload = Booking::Domain::Leg.new(
        load_location: "JPTYO", unload_location: "USLAX", voyage_number: "V001",
        load_time: Time.utc(2026, 9, 1, 8), unload_time: nil
      )
      expect do
        described_class.new(legs: [ leg_without_unload ])
      end.to raise_error(ArgumentError, /荷揚時刻/)
    end
  end

  describe "RouteSpecification#satisfied_by? の期限当日境界" do
    def itinerary_arriving(unload_time)
      Booking::Domain::CargoItinerary.new(legs: [ leg(load: "JPTYO", unload: "USLAX", unload_time: unload_time) ])
    end

    def spec_with_deadline
      Booking::Domain::RouteSpecification.new(
        origin: "JPTYO", destination: "USLAX", arrival_deadline: Date.new(2026, 11, 20)
      )
    end

    it "到着が期限当日（時刻付き）なら満たす" do
      expect(spec_with_deadline.satisfied_by?(itinerary_arriving(Time.utc(2026, 11, 20, 18)))).to be true
    end

    it "到着が期限翌日なら満たさない" do
      expect(spec_with_deadline.satisfied_by?(itinerary_arriving(Time.utc(2026, 11, 21, 1)))).to be false
    end
  end
end
