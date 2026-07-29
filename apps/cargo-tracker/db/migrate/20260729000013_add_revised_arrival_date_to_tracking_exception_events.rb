# frozen_string_literal: true

# 遅延対応の新到着予定日を構造化フィールドとして永続化する（T37・US19 対応報告）。
# 従来は resolution_notes 自由テキストだったものを、公開追跡の推定到着日へ反映可能にする。
class AddRevisedArrivalDateToTrackingExceptionEvents < ActiveRecord::Migration[8.0]
  def change
    add_column :tracking_exception_events, :revised_arrival_date, :date
  end
end
