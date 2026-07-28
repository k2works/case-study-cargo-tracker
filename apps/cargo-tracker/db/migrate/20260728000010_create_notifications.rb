# frozen_string_literal: true

# 通知送信記録（ADR-0002）。対象集約はポリモーフィック関連で参照する。
class CreateNotifications < ActiveRecord::Migration[8.0]
  def change
    create_table :notifications do |t|
      t.string   :notifiable_type, limit: 100, null: false # Cargo / Invoice 等
      t.bigint   :notifiable_id, null: false
      t.string   :event_type, limit: 50, null: false       # BOOKING_CONFIRMED / TRACKING_ISSUED 等
      t.string   :recipient_type, limit: 30, null: false    # SHIPPER / CONSIGNEE / OPERATOR
      t.string   :recipient_address, limit: 200, null: false
      t.string   :subject, limit: 200
      t.text     :body
      t.string   :status, limit: 20, null: false, default: "pending" # pending / sent / failed
      t.datetime :sent_at
      t.timestamps
    end
    add_index :notifications, %i[notifiable_type notifiable_id event_type]
  end
end
