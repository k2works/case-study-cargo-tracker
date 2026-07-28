# frozen_string_literal: true

module Routing
  module Infrastructure
    # voyages テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class VoyageRecord < ApplicationRecord
      self.table_name = "voyages"
      has_many :carrier_movement_records, class_name: "Routing::Infrastructure::CarrierMovementRecord",
                                          foreign_key: :voyage_id, dependent: :destroy, inverse_of: :voyage_record
    end
  end
end
