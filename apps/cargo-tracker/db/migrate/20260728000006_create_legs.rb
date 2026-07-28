# frozen_string_literal: true

# 輸送区間。経路（Routing）を扱う IT3 で本格利用する。IT2 では cargos に付随する空テーブルとして用意。
class CreateLegs < ActiveRecord::Migration[8.0]
  def change
    create_table :legs do |t|
      t.references :cargo, null: false, foreign_key: true
      t.string  :voyage_number, limit: 30, null: false
      t.string  :load_location_unlocode, limit: 5, null: false
      t.string  :unload_location_unlocode, limit: 5, null: false
      t.datetime :load_time
      t.datetime :unload_time
      t.integer :seq_number, null: false # 1 始まり
      t.timestamps
    end
    add_index :legs, %i[cargo_id seq_number], unique: true
  end
end
