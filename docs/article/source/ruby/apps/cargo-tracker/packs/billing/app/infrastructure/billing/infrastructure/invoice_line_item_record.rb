# frozen_string_literal: true

module Billing
  module Infrastructure
    # invoice_line_items テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class InvoiceLineItemRecord < ApplicationRecord
      self.table_name = "invoice_line_items"
    end
  end
end
