# frozen_string_literal: true

# 最新の荷役イベント（US15）を予約に反映する（追跡・状態同期の投影）。
class AddLastHandlingEventToCargos < ActiveRecord::Migration[8.0]
  def change
    add_column :cargos, :last_handling_event_type, :string, limit: 30
    add_column :cargos, :last_handling_event_location, :string, limit: 5
    add_column :cargos, :last_handling_event_voyage, :string, limit: 20
  end
end
