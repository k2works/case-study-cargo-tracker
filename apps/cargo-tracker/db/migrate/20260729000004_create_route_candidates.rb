# frozen_string_literal: true

# 経路候補（Estimation Context・US01）。Estimate に紐づく確定候補を永続化する（ADR-0004・IT7 統合）。
class CreateRouteCandidates < ActiveRecord::Migration[8.0]
  def change
    create_table :route_candidates do |t|
      t.references :estimate, null: false, foreign_key: { on_delete: :cascade }
      t.string   :voyage_number, limit: 20, null: false
      t.string   :transit_port, limit: 5
      t.integer  :transit_days, null: false
      t.decimal  :estimated_cost, precision: 12, scale: 2, null: false
      t.integer  :rank, null: false, default: 0
      t.timestamps
    end
    add_index :route_candidates, %i[estimate_id rank]
  end
end
