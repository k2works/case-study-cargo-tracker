# frozen_string_literal: true

module Handling
  module Infrastructure
    # handling_activities テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class HandlingActivityRecord < ApplicationRecord
      self.table_name = "handling_activities"
    end
  end
end
