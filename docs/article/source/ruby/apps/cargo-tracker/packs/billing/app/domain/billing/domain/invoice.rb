# frozen_string_literal: true

module Billing
  module Domain
    # 支払状態の不正遷移を表すドメイン例外（US23）。
    class InvalidPaymentTransitionError < StandardError; end

    # 請求書の集約ルート（US21 料金確定・US23 精算）。
    # 配送完了（DELIVERED）予約の確定料金から発行し、入金確認・期限超過を管理する。
    # Booking/Shipper への参照は越境識別子（string/id・ADR-0003）で保持する。
    class Invoice
      PAYMENT_TERM_DAYS = 30 # 支払期限は発行日 + 30 日

      attr_reader :invoice_number, :booking_id, :shipper_id, :amounts,
                  :payment_status, :issued_at, :due_date, :paid_at, :line_items

      # 請求書を発行する（PENDING で採番）。支払期限は発行日 + 30 日。
      def self.generate(invoice_number:, booking_id:, shipper_id:, amounts:, issued_at:)
        new(invoice_number: invoice_number, booking_id: booking_id, shipper_id: shipper_id,
            amounts: amounts, payment_status: PaymentStatus.initial, issued_at: issued_at, paid_at: nil)
      end

      # 永続化からの復元専用。
      def self.reconstitute(**attributes)
        new(**attributes)
      end

      def initialize(invoice_number:, booking_id:, shipper_id:, amounts:, payment_status:, issued_at:,
                     paid_at: nil, line_items: [])
        raise ArgumentError, "請求番号は必須です" if invoice_number.to_s.strip.empty?
        raise ArgumentError, "予約番号は必須です" if booking_id.to_s.strip.empty?

        @invoice_number = invoice_number
        @booking_id = booking_id
        @shipper_id = shipper_id
        @amounts = amounts
        @payment_status = payment_status
        @issued_at = issued_at
        @due_date = issued_at.to_date + PAYMENT_TERM_DAYS
        @paid_at = paid_at
        @line_items = line_items
      end

      # 料金調整（減額・補償費用）を明細として追加し、請求金額を再計算する（US21-6）。
      # 未精算（PENDING/OVERDUE）の請求書のみ調整でき、精算済（CONFIRMED/REFUNDED）は不可。
      # 調整後の請求金額は 0 を下回れない（過大減額の防止）。
      def add_adjustment(line_item)
        unless payment_status.unsettled?
          raise InvalidPaymentTransitionError, "未精算（支払待ち/期限超過）の請求書のみ調整できます（現在: #{payment_status}）"
        end

        adjusted_total = amounts.total.add(line_item.amount)
        raise ArgumentError, "調整後の請求金額が 0 を下回ります" if adjusted_total.amount.negative?

        @line_items = line_items + [ line_item ]
        @amounts = amounts.with(total: adjusted_total)
        line_item
      end

      # 料金調整（seq_number は 1 始まり）を取り消し、請求金額を再計算する（US21-6・T47a）。
      # 未精算（PENDING/OVERDUE）のみ取り消せる。
      def remove_adjustment(seq_number)
        unless payment_status.unsettled?
          raise InvalidPaymentTransitionError, "未精算の請求書のみ調整を取り消せます（現在: #{payment_status}）"
        end

        # seq_number は 1 始まり。0 や負値は負数インデックスの回り込み（末尾誤削除）を招くため明示的に弾く。
        raise ArgumentError, "取消対象の調整が存在しません: #{seq_number}" if seq_number.to_i < 1

        index = seq_number.to_i - 1
        removed = line_items[index]
        raise ArgumentError, "取消対象の調整が存在しません: #{seq_number}" if removed.nil?

        @line_items = line_items.reject.with_index { |_, i| i == index }
        # 明細金額は減算方向（負値）に正規化済みのため、取消は total から当該明細分を差し引いて戻す。
        @amounts = amounts.with(total: amounts.total.subtract(removed.amount))
        removed
      end

      # 金額の委譲アクセサ（明細表示・永続化で利用）。
      def base_amount = amounts.base
      def discount_rate = amounts.discount_rate
      def surcharge_amount = amounts.surcharge
      def tax_amount = amounts.tax
      def total_amount = amounts.total

      # 入金を確認して CONFIRMED に遷移する（US23）。未精算（PENDING/OVERDUE）のみ可能。
      # 期限超過（OVERDUE）後に遅れて入金されるケースを精算完了できるようにする（レビュー高）。
      def confirm_payment(paid_at:)
        unless payment_status.unsettled?
          raise InvalidPaymentTransitionError, "入金確認は未精算の請求書のみ可能です（現在: #{payment_status}）"
        end

        @payment_status = PaymentStatus.new(value: PaymentStatus::CONFIRMED)
        @paid_at = paid_at
      end

      # 支払期限を超過していれば OVERDUE に遷移する（US23 未払い通知の起点）。
      # 実際に遷移した場合のみ true を返す（通知を状態遷移に紐づけるため）。
      def mark_overdue_if_due(as_of:)
        return false unless payment_status.pending?
        return false unless as_of.to_date > due_date

        @payment_status = PaymentStatus.new(value: PaymentStatus::OVERDUE)
        true
      end
    end
  end
end
