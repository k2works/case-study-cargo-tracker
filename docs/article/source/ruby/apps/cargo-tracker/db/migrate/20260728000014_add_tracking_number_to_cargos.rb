# frozen_string_literal: true

# 追跡番号発行（US14）で発行された追跡番号を予約に保持する。
class AddTrackingNumberToCargos < ActiveRecord::Migration[8.0]
  def change
    add_column :cargos, :tracking_number, :string, limit: 20
    add_index :cargos, :tracking_number, unique: true
  end
end
