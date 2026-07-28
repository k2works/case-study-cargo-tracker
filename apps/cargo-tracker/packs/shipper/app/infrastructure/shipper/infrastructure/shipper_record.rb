# frozen_string_literal: true

module Shipper
  module Infrastructure
    # shippers テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class ShipperRecord < ApplicationRecord
      self.table_name = "shippers"
    end
  end
end
