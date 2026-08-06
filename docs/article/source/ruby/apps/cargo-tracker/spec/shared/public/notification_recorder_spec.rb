# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shared::Public::NotificationRecorder do
  subject(:recorder) { described_class.new }

  describe "#record（送信記録・ADR-0002）" do
    it "通知を pending で登録し送信完了で sent にする" do
      result = recorder.record(
        notifiable_type: "Cargo", notifiable_id: 1, event_type: "BOOKING_CONFIRMED",
        recipient_type: "SHIPPER", recipient_address: "shipper@example.com",
        subject: "予約確定", body: "ご予約が確定しました"
      )
      expect(result.status).to eq("sent")
      expect(result.sent_at).to be_present
    end

    it "対象・イベント種別で送信記録を検索できる" do
      recorder.record(notifiable_type: "Cargo", notifiable_id: 42, event_type: "ROUTE_NOTIFIED",
                      recipient_type: "SHIPPER", recipient_address: "s@example.com", subject: "経路", body: "x")
      records = recorder.for(notifiable_type: "Cargo", notifiable_id: 42)
      expect(records.size).to eq(1)
      expect(records.first.event_type).to eq("ROUTE_NOTIFIED")
    end

    it "宛先アドレスが空なら失敗記録（failed）にする" do
      result = recorder.record(notifiable_type: "Cargo", notifiable_id: 1, event_type: "X",
                               recipient_type: "SHIPPER", recipient_address: "", subject: "s", body: "b")
      expect(result.status).to eq("failed")
    end
  end
end
