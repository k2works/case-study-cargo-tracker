# frozen_string_literal: true

# 料金調整の種別（減額 REDUCTION / 補償 COMPENSATION）を明細に記録する（US21-6）。
class AddAdjustmentTypeToInvoiceLineItems < ActiveRecord::Migration[8.0]
  def change
    add_column :invoice_line_items, :adjustment_type, :string, limit: 30
  end
end
