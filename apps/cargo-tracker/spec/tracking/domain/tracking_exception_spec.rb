# frozen_string_literal: true

require "rails_helper"

# IT6: 追跡例外処理のドメイン層（US19 遅延・US20 破損/紛失）
RSpec.describe "Tracking Context 例外処理ドメイン" do
  describe Tracking::Domain::ExceptionType do
    it "DELAY/DAMAGE/LOST/CUSTOMS_HOLD を許可する" do
      %w[DELAY DAMAGE LOST CUSTOMS_HOLD].each do |value|
        expect(described_class.new(value: value).value).to eq(value)
      end
    end

    it "未知の例外種別は拒否する" do
      expect { described_class.new(value: "UNKNOWN") }.to raise_error(ArgumentError)
    end

    it "LOST はエスカレーションを要する（管理職通知）" do
      expect(described_class.new(value: "LOST").escalation_required?).to be true
    end

    it "LOST 以外はエスカレーション不要" do
      %w[DELAY DAMAGE CUSTOMS_HOLD].each do |value|
        expect(described_class.new(value: value).escalation_required?).to be false
      end
    end

    it "値等価で比較できる" do
      expect(described_class.new(value: "DELAY")).to eq(described_class.new(value: "DELAY"))
    end
  end

  describe Tracking::Domain::TrackingStatus do
    it "EXCEPTION を状態値として許可する（IT6）" do
      status = described_class.new(value: "EXCEPTION")
      expect(status.value).to eq("EXCEPTION")
      expect(status.exception?).to be true
    end
  end

  describe Tracking::Domain::TrackingExceptionEvent do
    let(:exception_type) { Tracking::Domain::ExceptionType.new(value: "DELAY") }

    it "例外イベントを生成できる（発生種別・日時・説明・場所）" do
      event = described_class.new(
        exception_type: exception_type,
        occurred_at: Time.utc(2026, 7, 29, 10, 0, 0),
        description: "台風による遅延",
        location_unlocode: "JPTYO"
      )
      expect(event.exception_type).to eq(exception_type)
      expect(event.description).to eq("台風による遅延")
      expect(event.location_unlocode).to eq("JPTYO")
      expect(event.resolved?).to be false
    end

    it "LOST 例外は生成時にエスカレーションフラグが立つ（US20）" do
      lost = described_class.new(
        exception_type: Tracking::Domain::ExceptionType.new(value: "LOST"),
        occurred_at: Time.utc(2026, 7, 29, 10, 0, 0),
        description: "紛失"
      )
      expect(lost.escalation_flag).to be true
    end

    it "LOST 以外はエスカレーションフラグが立たない" do
      delay = described_class.new(
        exception_type: exception_type,
        occurred_at: Time.utc(2026, 7, 29, 10, 0, 0),
        description: "遅延"
      )
      expect(delay.escalation_flag).to be false
    end

    it "対応内容を記録して解決できる（US19 対応報告・解決時刻と対応メモ）" do
      event = described_class.new(
        exception_type: exception_type,
        occurred_at: Time.utc(2026, 7, 29, 10, 0, 0),
        description: "遅延"
      )
      event.resolve(resolved_at: Time.utc(2026, 7, 30, 9, 0, 0), resolution_notes: "新到着予定日 8/5")
      expect(event.resolved?).to be true
      expect(event.resolution_notes).to eq("新到着予定日 8/5")
    end
  end

  describe "#{Tracking::Domain::TrackingActivity} の例外処理" do
    let(:booking_id) { "BKG-ABCD1234" }
    let(:delay_type) { Tracking::Domain::ExceptionType.new(value: "DELAY") }
    let(:lost_type) { Tracking::Domain::ExceptionType.new(value: "LOST") }

    def build_activity
      Tracking::Domain::TrackingActivity.issue(booking_id: booking_id).tap do |a|
        a.apply_handling("RECEIVE") # RECEIVED まで進める
      end
    end

    it "例外を登録すると輸送状態が EXCEPTION に遷移する（US19/US20）" do
      activity = build_activity
      activity.register_exception(exception_type: delay_type, occurred_at: Time.utc(2026, 7, 29), description: "遅延")

      expect(activity.transport_status.exception?).to be true
      expect(activity.active_exception?).to be true
    end

    it "LOST 例外はアクティブなエスカレーション対象を持つ（US20）" do
      activity = build_activity
      activity.register_exception(exception_type: lost_type, occurred_at: Time.utc(2026, 7, 29), description: "紛失")

      expect(activity.escalated?).to be true
    end

    it "例外を解決すると発生前の状態に復帰する（precondition・T30）" do
      activity = build_activity
      status_before = activity.transport_status
      exception = activity.register_exception(
        exception_type: delay_type, occurred_at: Time.utc(2026, 7, 29), description: "遅延"
      )

      activity.resolve_exception(exception, resolved_at: Time.utc(2026, 7, 30), resolution_notes: "解消")

      expect(activity.active_exception?).to be false
      expect(activity.transport_status).to eq(status_before)
    end

    it "アクティブな例外がないのに解決しようとするとエラー（precondition・T30）" do
      activity = build_activity
      foreign = Tracking::Domain::TrackingExceptionEvent.new(
        exception_type: delay_type, occurred_at: Time.utc(2026, 7, 29), description: "他"
      )

      expect { activity.resolve_exception(foreign, resolved_at: Time.utc(2026, 7, 30), resolution_notes: "x") }
        .to raise_error(ArgumentError, /解決対象/)
    end
  end
end
