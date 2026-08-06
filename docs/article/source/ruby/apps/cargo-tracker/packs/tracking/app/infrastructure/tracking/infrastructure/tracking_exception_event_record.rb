# frozen_string_literal: true

module Tracking
  module Infrastructure
    # tracking_exception_events テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class TrackingExceptionEventRecord < ApplicationRecord
      self.table_name = "tracking_exception_events"
    end
  end
end
