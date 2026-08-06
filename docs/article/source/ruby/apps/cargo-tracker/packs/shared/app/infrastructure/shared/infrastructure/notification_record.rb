# frozen_string_literal: true

module Shared
  module Infrastructure
    # notifications テーブルの Active Record レコード（通知送信記録・ADR-0002）。
    class NotificationRecord < ApplicationRecord
      self.table_name = "notifications"
    end
  end
end
