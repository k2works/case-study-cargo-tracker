# frozen_string_literal: true

module Billing
  module Public
    # 請求・精算の公開ファサード（アプリ層＝合成ルート／UI 向け）。
    # 内部のユースケース・リポジトリを隠蔽し、公開ビューを返す。
    class BillingService
      LineItemView = Data.define(:seq_number, :description, :amount, :adjustment_type, :adjusted_by, :reason)
      View = Data.define(:invoice_number, :booking_id, :base_amount, :discount_amount, :discount_percentage,
                         :surcharge_amount, :tax_amount, :total_amount, :payment_status, :issued_at, :due_date,
                         :paid_at, :line_items)

      def initialize(repository: Infrastructure::ActiveRecordInvoiceRepository.new)
        @repository = repository
      end

      # 輸送料金算出・請求書発行（US21/US22）。:ok / :invalid / :not_found / :already_invoiced。
      # issued_at は発行日時（既定は現在時刻）。開発シードで過去日の請求を作る等に用いる。
      def calculate_freight(booking_id, booking_service: Booking::Public::CargoBookingService.new,
                            shipper_directory: Shipper::Public::ShipperDirectory.new, issued_at: nil)
        clock = issued_at ? -> { issued_at } : -> { Time.current }
        Application::CalculateFreight.new(
          repository: @repository, booking_service: booking_service,
          shipper_directory: shipper_directory, clock: clock
        ).call(booking_id)
      end

      # 精算処理（US23・入金確認→CONFIRMED→予約 SETTLED）。:ok / :not_found / :payment_failed / :invalid。
      def settle(invoice_number, payment_gateway: Infrastructure::StubPaymentGateway.new,
                 booking_service: Booking::Public::CargoBookingService.new)
        Application::SettleInvoice.new(
          repository: @repository, payment_gateway: payment_gateway, booking_service: booking_service
        ).call(invoice_number)
      end

      # 未払い通知の駆動（US23-5）。期限超過の PENDING を OVERDUE にし経理へ通知する。
      def mark_overdue(as_of: nil)
        Application::MarkOverdueInvoices.new(repository: @repository).call(as_of: as_of)
      end

      # 料金調整（US21-6）。減額・補償費用を明細に追加し請求金額を再計算する。:ok / :not_found / :invalid。
      def adjust(invoice_number, description:, amount:, adjustment_type:, adjusted_by: nil, reason: nil)
        Application::AdjustFreight.new(repository: @repository).call(
          invoice_number: invoice_number, description: description, amount: amount,
          adjustment_type: adjustment_type, adjusted_by: adjusted_by, reason: reason
        )
      end

      # 料金調整の取消（US21-6・T47a）。:ok / :not_found / :invalid。
      def cancel_adjustment(invoice_number, seq_number:)
        Application::CancelAdjustment.new(repository: @repository).call(
          invoice_number: invoice_number, seq_number: seq_number
        )
      end

      def find_invoice(invoice_number)
        invoice = @repository.find_by_invoice_number(invoice_number)
        invoice && to_view(invoice)
      end

      # 予約番号に紐づく請求書番号（例外管理→請求への導線・T47c）。なければ nil。
      def invoice_number_for_booking(booking_id)
        invoice = @repository.find_by_booking_id(booking_id)
        invoice&.invoice_number
      end

      def invoices
        @repository.all.map { |i| to_view(i) }
      end

      private

      def to_view(invoice)
        base = invoice.base_amount.amount
        discount = (base * invoice.discount_rate.rate).to_i
        View.new(
          invoice_number: invoice.invoice_number, booking_id: invoice.booking_id,
          base_amount: base.to_i, discount_amount: discount,
          discount_percentage: (invoice.discount_rate.rate * 100).to_i,
          surcharge_amount: invoice.surcharge_amount.amount.to_i,
          tax_amount: invoice.tax_amount.amount.to_i, total_amount: invoice.total_amount.amount.to_i,
          payment_status: invoice.payment_status.value, issued_at: invoice.issued_at,
          due_date: invoice.due_date, paid_at: invoice.paid_at,
          line_items: invoice.line_items.each_with_index.map do |li, i|
            LineItemView.new(seq_number: i + 1, description: li.description, amount: li.amount.amount.to_i,
                             adjustment_type: li.adjustment_type, adjusted_by: li.adjusted_by, reason: li.reason)
          end
        )
      end
    end
  end
end
