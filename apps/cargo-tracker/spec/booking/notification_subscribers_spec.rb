# frozen_string_literal: true

require "rails_helper"

# US12/US13: ドメインイベント駆動の通知（ADR-0002）。
# 集約が発行したイベントを購読ハンドラが受け、通知記録が残ることを検証する。
RSpec.describe Booking::Application::NotificationSubscribers do
  let(:recorder) { Shared::Public::NotificationRecorder.new }
  let(:shipper_directory) do
    shipper = Struct.new(:email)
    Class.new do
      define_method(:find) { |_id| shipper.new("shipper@example.com") }
    end.new
  end

  before do
    DomainEvents.reset!
    described_class.install!(recorder: recorder, shipper_directory: shipper_directory)
  end

  after { DomainEvents.reset! }

  it "cargo_routed で荷主へ経路通知が記録される（US12）" do
    DomainEvents.publish("cargo_routed",
                         { cargo_id: "BKG-1", shipper_id: 1, origin: "JPTYO",
                           destination: "USLAX", expected_arrival_time: Time.utc(2026, 11, 20) })
    records = recorder.for(notifiable_type: "Cargo", notifiable_id: "BKG-1")
    expect(records.map(&:event_type)).to include("ROUTE_NOTIFIED")
    expect(records.first.recipient_address).to eq("shipper@example.com")
    expect(records.first.status).to eq("sent")
  end

  it "cargo_confirmed で経路設計者へ追跡番号発行依頼が記録される（US13）" do
    DomainEvents.publish("cargo_confirmed", { cargo_id: "BKG-2" })
    records = recorder.for(notifiable_type: "Cargo", notifiable_id: "BKG-2")
    expect(records.map(&:event_type)).to include("TRACKING_REQUESTED")
    expect(records.first.recipient_type).to eq("OPERATOR")
  end

  it "cargo_cancelled で荷主へキャンセル確認が記録される（US13）" do
    DomainEvents.publish("cargo_cancelled", { cargo_id: "BKG-3", shipper_id: 1 })
    records = recorder.for(notifiable_type: "Cargo", notifiable_id: "BKG-3")
    expect(records.map(&:event_type)).to include("BOOKING_CANCELLED")
  end
end
