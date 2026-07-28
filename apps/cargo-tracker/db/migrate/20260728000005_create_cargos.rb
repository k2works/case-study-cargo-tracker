# frozen_string_literal: true

class CreateCargos < ActiveRecord::Migration[8.0]
  def change
    create_table :cargos do |t|
      t.string :booking_id, limit: 20, null: false          # 予約番号（業務キー）
      t.references :shipper, null: false, foreign_key: true   # shippers.id（越境識別子・ADR-0003）
      t.string  :cargo_type, limit: 30, null: false           # GENERAL / HAZARDOUS / REFRIGERATED
      t.decimal :weight_kg, precision: 10, scale: 3, null: false
      t.string  :origin_unlocode, limit: 5, null: false
      t.string  :destination_unlocode, limit: 5, null: false
      t.date    :arrival_deadline, null: false
      t.string  :booking_status, limit: 30, null: false, default: "preliminary" # BookingStatus 9 値
      t.decimal :dimension_length, precision: 10, scale: 3
      t.decimal :dimension_width, precision: 10, scale: 3
      t.decimal :dimension_height, precision: 10, scale: 3
      t.integer :quantity
      t.string  :description, limit: 500
      t.string  :hazardous_class, limit: 10                   # HAZARDOUS のみ
      t.string  :un_number, limit: 10
      t.string  :proper_shipping_name, limit: 200
      t.decimal :min_temperature, precision: 10, scale: 3     # REFRIGERATED のみ
      t.decimal :max_temperature, precision: 10, scale: 3
      t.string  :temperature_unit, limit: 20                  # CELSIUS / FAHRENHEIT
      t.integer :lock_version, null: false, default: 0        # 楽観ロック
      t.timestamps
    end
    add_index :cargos, :booking_id, unique: true
  end
end
