# frozen_string_literal: true

# 荷役の冪等キーに一意制約を張り、並行 POST の二重登録を DB レベルで防ぐ（T35・最終防衛）。
# voyage_number は NULL 可のため、NULL は空文字に正規化した式インデックスで一意性を担保する。
class AddUniqueIndexToHandlingActivities < ActiveRecord::Migration[8.0]
  def up
    execute <<~SQL.squish
      CREATE UNIQUE INDEX idx_handling_activities_idempotency
      ON handling_activities (booking_id, event_type, event_completion_time, COALESCE(voyage_number, ''))
    SQL
  end

  def down
    remove_index :handling_activities, name: :idx_handling_activities_idempotency
  end
end
