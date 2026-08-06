# frozen_string_literal: true

module Tracking
  module Infrastructure
    # tracking_handling_events テーブルの Active Record レコード（追跡イベント履歴・内部専用）。
    class TrackingHandlingEventRecord < ApplicationRecord
      self.table_name = "tracking_handling_events"
    end
  end
end
