# frozen_string_literal: true

# 料金調整の監査証跡に「日時」を追加する（US21-6・T47b・IT9 レビュー高）。
# 担当者（adjusted_by）・理由（reason）に続き、調整実施日時を明示的に保持する。
class AddAdjustedAtToInvoiceLineItems < ActiveRecord::Migration[8.0]
  def change
    add_column :invoice_line_items, :adjusted_at, :datetime, null: true
  end
end
