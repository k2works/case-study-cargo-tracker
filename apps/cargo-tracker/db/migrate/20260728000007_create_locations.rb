# frozen_string_literal: true

# 場所マスタ（Location 共有カーネル）。UN/LOCODE で識別される港湾・地点。
class CreateLocations < ActiveRecord::Migration[8.0]
  def change
    create_table :locations do |t|
      t.string :unlocode, limit: 5, null: false  # UN/LOCODE（業務キー。例: JPTYO）
      t.string :name, limit: 100, null: false
      t.string :country_code, limit: 2            # ISO 3166-1 alpha-2
      t.string :time_zone, limit: 50
      t.timestamps
    end
    add_index :locations, :unlocode, unique: true
  end
end
