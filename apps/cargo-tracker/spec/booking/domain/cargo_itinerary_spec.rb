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
  end
end
