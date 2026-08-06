# frozen_string_literal: true

class CreateShippers < ActiveRecord::Migration[8.0]
  def change
    create_table :shippers do |t|
      t.string  :shipper_code, limit: 20, null: false  # SHP-XXXXXX 形式
      t.string  :shipper_type, limit: 20, null: false  # INDIVIDUAL / CORPORATE
      t.string  :name, limit: 200, null: false
      t.string  :address, limit: 500                   # Address 値オブジェクト（最大 500 文字）
      t.string  :email, limit: 200, null: false
      t.string  :phone, limit: 50
      t.string  :contract_number, limit: 50            # 法人のみ（NULLable）
      t.decimal :discount_rate, precision: 5, scale: 4, default: 0.0 # 0.0000〜0.3000（最大 30%）
      t.timestamps
    end
    add_index :shippers, :shipper_code, unique: true
  end
end
