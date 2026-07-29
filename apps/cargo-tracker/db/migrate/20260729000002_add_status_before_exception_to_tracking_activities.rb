# frozen_string_literal: true

# 例外発生前の輸送状態を集約状態として永続化する（US19/US20 対応報告での状態復帰・T30）。
# 荷役履歴からの再導出では US17 手動更新由来の状態を復元できないため、集約自身が保持する。
class AddStatusBeforeExceptionToTrackingActivities < ActiveRecord::Migration[8.0]
  def change
    add_column :tracking_activities, :status_before_exception, :string, limit: 30
  end
end
