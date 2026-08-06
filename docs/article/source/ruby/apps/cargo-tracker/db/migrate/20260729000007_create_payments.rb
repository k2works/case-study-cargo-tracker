# frozen_string_literal: true

# 入金記録（Billing Context・US23）。決済機関連携での入金確認を記録する。
class CreatePayments < ActiveRecord::Migration[8.0]
  def change
    create_table :payments do |t|
      t.references :invoice, null: false, foreign_key: true
      t.integer  :paid_amount_value, null: false
      t.string   :paid_amount_currency, limit: 3, null: false
      t.datetime :paid_at, null: false
      t.string   :payment_method, limit: 30, null: false # BANK_TRANSFER / CREDIT_CARD 等
      t.string   :transaction_reference, limit: 100 # 外部決済 ID
      t.timestamps
    end
  end
end
