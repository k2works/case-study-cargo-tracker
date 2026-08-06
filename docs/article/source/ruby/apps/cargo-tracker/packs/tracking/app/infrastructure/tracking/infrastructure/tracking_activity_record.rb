# frozen_string_literal: true

module Tracking
  module Infrastructure
    # tracking_activities テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class TrackingActivityRecord < ApplicationRecord
      self.table_name = "tracking_activities"
    end
  end
end
