# frozen_string_literal: true

require "rails_helper"

RSpec.describe "Handling Context ドメイン" do
  def snapshot
    Handling::Domain::CargoSnapshot.new(
      booking_id: "BKG-1", origin: "JPTYO", destination: "USLAX",
      leg_locations: %w[JPTYO SGSIN USLAX]
    )
  end

  def confirmation
    Handling::Domain::RecipientConfirmation.new(recipient_name: "山田", confirmation_code: "OK123")
  end

  describe Handling::Domain::HandlingType do
    it "RECEIVE/LOAD/UNLOAD/CLAIM を許可する" do
      %w[RECEIVE LOAD UNLOAD CLAIM].each { |v| expect(described_class.new(value: v).value).to eq(v) }
    end

    it "LOAD/UNLOAD は航海番号が必須" do
      expect(described_class.new(value: "LOAD").requires_voyage_number?).to be true
      expect(described_class.new(value: "RECEIVE").requires_voyage_number?).to be false
    end

    it "未知の種別は拒否する" do
      expect { described_class.new(value: "X") }.to raise_error(ArgumentError)
    end
  end

  describe Handling::Domain::RecipientConfirmation do
    it "荷受人名 + 署名または確認コードで有効" do
      expect(described_class.new(recipient_name: "山田", signature: "sign").valid?).to be true
      expect(described_class.new(recipient_name: "山田", confirmation_code: "C1").valid?).to be true
    end

    it "確認手段がなければ無効" do
      expect(described_class.new(recipient_name: "山田").valid?).to be false
    end
  end

  describe Handling::Domain::HandlingActivity do
    def register(type:, location:, voyage: "V001", confirmation: nil)
      described_class.register(
        booking_id: "BKG-1", type: Handling::Domain::HandlingType.new(value: type),
        location: location, completion_time: Time.utc(2026, 9, 10, 12),
        voyage_number: voyage, operator_name: "作業員A", recipient_confirmation: confirmation
      )
    end

    it "受領（RECEIVE）を記録できる" do
      activity = register(type: "RECEIVE", location: "JPTYO", voyage: nil)
      expect(activity.type.value).to eq("RECEIVE")
    end

    it "LOAD は航海番号がなければ拒否する" do
      expect { register(type: "LOAD", location: "JPTYO", voyage: nil) }.to raise_error(ArgumentError, /航海番号/)
    end

    it "CLAIM は荷受人確認がなければ拒否する（US16）" do
      expect { register(type: "CLAIM", location: "USLAX", voyage: nil, confirmation: nil) }
        .to raise_error(ArgumentError, /荷受人確認/)
    end

    it "CLAIM は荷受人確認ありで記録できる（US16）" do
      activity = register(type: "CLAIM", location: "USLAX", voyage: nil, confirmation: confirmation)
      expect(activity.type.value).to eq("CLAIM")
    end

    describe "#route_check（荷役妥当性・US15）" do
      it "旅程に含まれる港での LOAD は :ok" do
        expect(register(type: "LOAD", location: "SGSIN").route_check(snapshot)).to eq(:ok)
      end

      it "旅程外の港での LOAD は :misrouted" do
        expect(register(type: "LOAD", location: "CNSHA").route_check(snapshot)).to eq(:misrouted)
      end

      it "出発港以外での RECEIVE は :warning（記録は阻止しない）" do
        expect(register(type: "RECEIVE", location: "CNSHA", voyage: nil).route_check(snapshot)).to eq(:warning)
      end

      it "目的港での CLAIM は :ok" do
        expect(register(type: "CLAIM", location: "USLAX", voyage: nil, confirmation: confirmation).route_check(snapshot)).to eq(:ok)
      end

      it "旅程に含まれる港での UNLOAD は :ok" do
        expect(register(type: "UNLOAD", location: "SGSIN").route_check(snapshot)).to eq(:ok)
      end

      it "旅程外の港での UNLOAD は :misrouted" do
        expect(register(type: "UNLOAD", location: "CNSHA").route_check(snapshot)).to eq(:misrouted)
      end

      it "目的港以外での CLAIM は :warning（記録は阻止しない）" do
        expect(register(type: "CLAIM", location: "SGSIN", voyage: nil, confirmation: confirmation).route_check(snapshot)).to eq(:warning)
      end
    end
  end
end
