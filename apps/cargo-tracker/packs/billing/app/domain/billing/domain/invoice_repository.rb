# frozen_string_literal: true

module Billing
  module Domain
    # 請求書リポジトリの抽象ポート（出力ポート・ADR-0001）。
    class InvoiceRepository
      def save(_invoice) = raise NotImplementedError
      def find_by_invoice_number(_invoice_number) = raise NotImplementedError
      def find_by_booking_id(_booking_id) = raise NotImplementedError
      def exists_for_booking?(_booking_id) = raise NotImplementedError
      def all = raise NotImplementedError
    end
  end
end
