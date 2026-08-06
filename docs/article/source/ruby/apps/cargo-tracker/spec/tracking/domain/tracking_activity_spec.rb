# frozen_string_literal: true

require "rails_helper"

RSpec.describe "Tracking Context ドメイン" do
  describe Tracking::Domain::TrackingNumber do
    it "TRK- プレフィックス付き追跡番号を生成する" do
      expect(described_class.generate.value).to match(/\ATRK-[0-9A-F]{8}\z/)
    end

    it "不正形式は拒否する" do
      expect { described_class.new(value: "X") }.to raise_error(ArgumentError)
    end
  end

  describe Tracking::Domain::TrackingStatus do
    it "NOT_RECEIVED を初期状態とする" do
      expect(described_class.initial.value).to eq("NOT_RECEIVED")
      expect(described_class.initial.not_received?).to be true
    end

    it "未知の状態は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end
  end

  describe Tracking::Domain::TrackingActivity do
    let(:booking_id) { "BKG-ABCD1234" }

    it "追跡番号発行で NOT_RECEIVED の追跡レコードを生成する（US14）" do
      activity = described_class.issue(booking_id: booking_id)
      expect(activity.tracking_number.value).to match(/\ATRK-/)
      expect(activity.booking_id).to eq(booking_id)
      expect(activity.transport_status.not_received?).to be true
    end

    it "指定の追跡番号・状態で復元できる（reconstitute）" do
      activity = described_class.reconstitute(
        tracking_number: Tracking::Domain::TrackingNumber.new(value: "TRK-0000ABCD"),
        booking_id: booking_id, transport_status: Tracking::Domain::TrackingStatus.new(value: "RECEIVED")
      )
      expect(activity.transport_status.value).to eq("RECEIVED")
    end
  end
end
