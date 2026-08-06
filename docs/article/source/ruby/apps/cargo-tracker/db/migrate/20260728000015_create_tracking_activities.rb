# frozen_string_literal: true

# 貨物追跡レコード（Tracking Context・US14）。集約ルート。
# booking_id は Booking Context の自然キーを論理参照（DB 外部キーは張らない・ADR-0001/0003）。
class CreateTrackingActivities < ActiveRecord::Migration[8.0]
  def change
    create_table :tracking_activities do |t|
      t.string  :tracking_number, limit: 20, null: false
      t.string  :booking_id, limit: 20, null: false
      t.string  :transport_status, limit: 30, null: false, default: "NOT_RECEIVED"
      t.integer :lock_version, null: false, default: 0
      t.timestamps
    end
    add_index :tracking_activities, :tracking_number, unique: true
    add_index :tracking_activities, :booking_id, unique: true
  end
end
