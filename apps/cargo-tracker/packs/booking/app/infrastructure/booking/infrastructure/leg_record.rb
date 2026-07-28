# frozen_string_literal: true

module Booking
  module Infrastructure
    # legs テーブルの Active Record レコード（旅程の 1 区間・永続化アダプタ内部専用）。
    class LegRecord < ApplicationRecord
      self.table_name = "legs"
    end
  end
end
