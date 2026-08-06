# frozen_string_literal: true

class CreateVoyages < ActiveRecord::Migration[8.0]
  def change
    create_table :voyages do |t|
      t.string :voyage_number, limit: 20, null: false  # 航海番号（業務キー）
      t.string :carrier_name, limit: 100, null: false   # 運送会社
      t.string :ship_name, limit: 100                    # 船名
      t.string :supported_cargo_types, limit: 100, null: false, default: "GENERAL"
      # 対応貨物種別（カンマ区切り: GENERAL,HAZARDOUS,REFRIGERATED）。US07 絞り込み用
      t.timestamps
    end
    add_index :voyages, :voyage_number, unique: true
  end
end
