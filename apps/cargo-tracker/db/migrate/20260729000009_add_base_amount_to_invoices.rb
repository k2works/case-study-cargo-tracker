# frozen_string_literal: true

# 基本料金を請求書集約の状態として永続化する（US21・total からの逆算では割増を無視して
# 誤差が出るため。状態の再導出を避ける・T33）。
class AddBaseAmountToInvoices < ActiveRecord::Migration[8.0]
  def change
    add_column :invoices, :base_amount_value, :integer
  end
end
