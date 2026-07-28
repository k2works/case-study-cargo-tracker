# frozen_string_literal: true

module Shared
  module Infrastructure
    # locations テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class LocationRecord < ApplicationRecord
      self.table_name = "locations"
    end
  end
end
