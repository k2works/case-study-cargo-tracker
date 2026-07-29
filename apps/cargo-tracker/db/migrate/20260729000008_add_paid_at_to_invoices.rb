# frozen_string_literal: true

# 入金確認日時を請求書集約の状態として永続化する（US23・状態の再導出を避ける・T33）。
class AddPaidAtToInvoices < ActiveRecord::Migration[8.0]
  def change
    add_column :invoices, :paid_at, :datetime
  end
end
