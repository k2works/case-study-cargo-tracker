# frozen_string_literal: true

module Billing
  module Infrastructure
    # invoices テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class InvoiceRecord < ApplicationRecord
      self.table_name = "invoices"
    end
  end
end
