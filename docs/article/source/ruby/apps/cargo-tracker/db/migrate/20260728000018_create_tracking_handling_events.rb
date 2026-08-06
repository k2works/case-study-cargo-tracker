# frozen_string_literal: true

# 追跡イベント履歴（Tracking Context・US15/US17）。TrackingActivity 配下の時系列イベント。
class CreateTrackingHandlingEvents < ActiveRecord::Migration[8.0]
  def change
    create_table :tracking_handling_events do |t|
      t.references :tracking_activity, null: false, foreign_key: true
      t.string   :event_type, limit: 30, null: false
      t.datetime :event_time, null: false
      t.string   :location_unlocode, limit: 5
      t.string   :voyage_number, limit: 20
      t.timestamps
    end
    add_index :tracking_handling_events, %i[tracking_activity_id event_time]
  end
end
