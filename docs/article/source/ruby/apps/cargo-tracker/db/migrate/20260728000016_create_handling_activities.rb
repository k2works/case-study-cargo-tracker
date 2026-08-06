# frozen_string_literal: true

# 荷役作業記録（Handling Context・US15/US16）。集約ルート。
# booking_id は Booking Context の自然キーを論理参照（DB 外部キーは張らない・ADR-0001/0003）。
class CreateHandlingActivities < ActiveRecord::Migration[8.0]
  def change
    create_table :handling_activities do |t|
      t.string   :booking_id, limit: 20, null: false
      t.string   :event_type, limit: 30, null: false # RECEIVE / LOAD / UNLOAD / CLAIM
      t.datetime :event_completion_time, null: false
      t.string   :location_unlocode, limit: 5, null: false
      t.string   :voyage_number, limit: 20
      t.string   :operator_name, limit: 200
      t.string   :recipient_name, limit: 200          # US16 引取: 荷受人名
      t.string   :recipient_signature, limit: 200     # US16 引取: 署名（署名または確認コード）
      t.string   :recipient_confirmation_code, limit: 50 # US16 引取: 確認コード
      t.timestamps
    end
    add_index :handling_activities, :booking_id
    add_index :handling_activities, %i[booking_id event_completion_time]
  end
end
