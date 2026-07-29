# frozen_string_literal: true

# 越境識別子 shipper_id（ADR-0003・荷主別通知/集計用）と割増額を請求書に永続化する。
# 集約状態の消失（再構成時 nil 化）と明細のサーチャージ不可視を解消する（IT7 レビュー対応）。
class AddShipperAndSurchargeToInvoices < ActiveRecord::Migration[8.0]
  def change
    add_column :invoices, :shipper_id, :bigint
    add_column :invoices, :surcharge_amount_value, :integer, null: false, default: 0
  end
end
