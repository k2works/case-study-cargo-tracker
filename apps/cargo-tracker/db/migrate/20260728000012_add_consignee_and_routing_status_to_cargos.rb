# frozen_string_literal: true

# cargos に荷受人（US12 通知先）と経路状態（NOT_ROUTED/ROUTED/MISROUTED）を追加する。
# data-model 論理モデルの IT4+ 予定分を実カラム化する。
class AddConsigneeAndRoutingStatusToCargos < ActiveRecord::Migration[8.0]
  def change
    add_column :cargos, :consignee_name, :string, limit: 200
    add_column :cargos, :consignee_email, :string, limit: 200
    add_column :cargos, :routing_status, :string, limit: 30, null: false, default: "NOT_ROUTED"
  end
end
