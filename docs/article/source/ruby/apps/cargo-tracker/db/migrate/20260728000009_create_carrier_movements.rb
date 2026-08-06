# frozen_string_literal: true

class CreateCarrierMovements < ActiveRecord::Migration[8.0]
  def change
    create_table :carrier_movements do |t|
      t.references :voyage, null: false, foreign_key: true
      t.string   :departure_location_unlocode, limit: 5, null: false # FK → locations.unlocode
      t.string   :arrival_location_unlocode, limit: 5, null: false
      t.datetime :departure_date, null: false
      t.datetime :arrival_date, null: false
      t.integer  :seq_number, null: false # 区間順序（1 始まり）
      t.timestamps
    end
    add_index :carrier_movements, %i[voyage_id seq_number], unique: true
  end
end
