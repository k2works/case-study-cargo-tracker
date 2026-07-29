# frozen_string_literal: true

require "rails_helper"

# T29: 楽観ロック（lock_version）の競合回帰テスト。
# ロックを導入した以上、並行更新時に StaleObjectError が発生することを固定する。
RSpec.describe "追跡活動の楽観ロック競合（T29）", type: :model do
  let(:repository) { Tracking::Infrastructure::ActiveRecordTrackingRepository.new }

  before do
    Tracking::Infrastructure::TrackingActivityRecord.create!(
      tracking_number: "TRK-LOCK0001", booking_id: "BKG-LOCK01", transport_status: "NOT_RECEIVED"
    )
  end

  it "同一レコードを並行更新すると後発の保存が StaleObjectError になる" do
    record_class = Tracking::Infrastructure::TrackingActivityRecord
    first = record_class.find_by!(tracking_number: "TRK-LOCK0001")
    second = record_class.find_by!(tracking_number: "TRK-LOCK0001")

    first.update!(transport_status: "RECEIVED") # lock_version 0 → 1

    expect { second.update!(transport_status: "LOADED") } # stale（lock_version 0 のまま）
      .to raise_error(ActiveRecord::StaleObjectError)
  end

  it "更新のたびに lock_version が増加する" do
    record = Tracking::Infrastructure::TrackingActivityRecord.find_by!(tracking_number: "TRK-LOCK0001")
    expect { record.update!(transport_status: "RECEIVED") }.to change { record.lock_version }.by(1)
  end
end
