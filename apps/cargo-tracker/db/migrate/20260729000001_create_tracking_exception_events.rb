# frozen_string_literal: true

# 追跡例外イベント（Tracking Context・US19 遅延 / US20 破損・紛失）。
# TrackingActivity 集約配下の例外エンティティ。楽観ロックは集約ルート側で担保する。
class CreateTrackingExceptionEvents < ActiveRecord::Migration[8.0]
  def change
    create_table :tracking_exception_events do |t|
      t.references :tracking_activity, null: false, foreign_key: true
      t.string   :exception_type, limit: 50, null: false
      t.datetime :occurred_at, null: false
      t.boolean  :escalation_flag, null: false, default: false
      t.string   :description, limit: 500
      # 発生場所（domain-model の TrackingLocation を永続化・設計反映 IT6 項目7）
      t.string   :location_unlocode, limit: 5
      t.datetime :resolved_at
      t.text     :resolution_notes
      t.timestamps
    end
    add_index :tracking_exception_events, %i[tracking_activity_id occurred_at]
  end
end
