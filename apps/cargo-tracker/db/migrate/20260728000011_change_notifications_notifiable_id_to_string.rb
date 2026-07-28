# frozen_string_literal: true

# 通知対象の識別子はドメインの自然キー（booking_id 等の文字列）を保持するため string 化する（ADR-0002）。
class ChangeNotificationsNotifiableIdToString < ActiveRecord::Migration[8.0]
  def up
    change_column :notifications, :notifiable_id, :string, limit: 50, null: false
  end

  def down
    change_column :notifications, :notifiable_id, :bigint, null: false
  end
end
