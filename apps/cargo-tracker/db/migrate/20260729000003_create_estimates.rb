# frozen_string_literal: true

# 輸送見積（Estimation Context・US01）。集約ルート Estimate の永続化。
class CreateEstimates < ActiveRecord::Migration[8.0]
  def change
    create_table :estimates do |t|
      t.string   :estimate_uuid, null: false
      t.string   :origin_unlocode, limit: 5, null: false
      t.string   :destination_unlocode, limit: 5, null: false
      t.date     :arrival_deadline, null: false
      t.string   :cargo_type, limit: 30, null: false # GENERAL / HAZARDOUS / REFRIGERATED
      t.decimal  :weight_kg, precision: 10, scale: 3, null: false
      t.string   :status, limit: 20, null: false, default: "CREATED" # CREATED / EXPIRED
      t.timestamps
    end
    add_index :estimates, :estimate_uuid, unique: true
  end
end
