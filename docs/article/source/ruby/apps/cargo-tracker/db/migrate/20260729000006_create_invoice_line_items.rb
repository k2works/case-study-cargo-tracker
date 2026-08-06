# frozen_string_literal: true

# 請求明細（Billing Context・US21/US22）。基本料金・割引・割増・消費税などの明細行。
class CreateInvoiceLineItems < ActiveRecord::Migration[8.0]
  def change
    create_table :invoice_line_items do |t|
      t.references :invoice, null: false, foreign_key: true
      t.string   :description, limit: 200, null: false
      t.integer  :amount_value, null: false
      t.string   :amount_currency, limit: 3, null: false
      t.integer  :seq_number, null: false # 1 始まり
      t.timestamps
    end
    add_index :invoice_line_items, %i[invoice_id seq_number]
  end
end
