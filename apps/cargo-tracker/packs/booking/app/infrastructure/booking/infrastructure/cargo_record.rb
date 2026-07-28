# frozen_string_literal: true

module Booking
  module Infrastructure
    # cargos テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class CargoRecord < ApplicationRecord
      self.table_name = "cargos"

      has_many :leg_records, -> { order(:seq_number) },
               class_name: "Booking::Infrastructure::LegRecord",
               foreign_key: :cargo_id, dependent: :delete_all, inverse_of: false
    end
  end
end
