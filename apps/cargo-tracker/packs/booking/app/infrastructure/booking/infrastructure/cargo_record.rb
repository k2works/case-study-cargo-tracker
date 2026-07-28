# frozen_string_literal: true

module Booking
  module Infrastructure
    # cargos テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class CargoRecord < ApplicationRecord
      self.table_name = "cargos"
    end
  end
end
