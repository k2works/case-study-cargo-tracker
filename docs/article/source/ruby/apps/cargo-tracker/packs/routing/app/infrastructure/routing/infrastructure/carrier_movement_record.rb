# frozen_string_literal: true

module Routing
  module Infrastructure
    # carrier_movements テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class CarrierMovementRecord < ApplicationRecord
      self.table_name = "carrier_movements"
      belongs_to :voyage_record, class_name: "Routing::Infrastructure::VoyageRecord",
                                 foreign_key: :voyage_id, inverse_of: :carrier_movement_records
    end
  end
end
