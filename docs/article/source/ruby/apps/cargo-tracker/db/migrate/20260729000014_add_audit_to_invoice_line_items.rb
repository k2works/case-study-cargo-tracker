# frozen_string_literal: true

# 料金調整の監査証跡（担当者・理由）を明細に記録する（US21-6・T47b）。
class AddAuditToInvoiceLineItems < ActiveRecord::Migration[8.0]
  def change
    add_column :invoice_line_items, :adjusted_by, :string, limit: 100
    add_column :invoice_line_items, :reason, :string, limit: 200
  end
end
