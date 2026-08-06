# frozen_string_literal: true

# 請求書（Billing Context・US21/US23）。集約ルート Invoice の永続化。booking_id UK で二重請求を防止。
class CreateInvoices < ActiveRecord::Migration[8.0]
  def change
    create_table :invoices do |t|
      t.string   :invoice_number, limit: 30, null: false
      t.string   :booking_id, limit: 20, null: false
      t.integer  :total_amount_value, null: false
      t.string   :total_amount_currency, limit: 3, null: false
      t.decimal  :tax_rate, precision: 5, scale: 4, null: false, default: "0.1"
      t.decimal  :tax_amount, precision: 15, scale: 2, null: false, default: "0"
      t.string   :payment_status, limit: 30, null: false # PENDING / CONFIRMED / OVERDUE / REFUNDED
      t.datetime :issued_at
      t.date     :due_date
      t.integer  :discount_amount_value
      t.string   :discount_amount_currency, limit: 3
      t.integer  :lock_version, null: false, default: 0
      t.timestamps
    end
    add_index :invoices, :invoice_number, unique: true
    add_index :invoices, :booking_id, unique: true # 二重請求防止
  end
end
