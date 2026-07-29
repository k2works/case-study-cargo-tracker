# frozen_string_literal: true

module Billing
  # 請求管理（Billing Context・US21/US22/US23）。経理担当者ロール。
  # Billing Context へは公開 API 経由でのみアクセスする。
  class InvoicesController < ApplicationController
    before_action -> { require_role(:billing) }

    def index
      @invoices = billing_service.invoices
    end

    def show
      @invoice = billing_service.find_invoice(params[:id])
      redirect_to billing_invoices_path, alert: "請求書が見つかりません" if @invoice.nil?
    end

    # 引取済（DELIVERED）予約の輸送料金を算出し請求書を発行する（US21/US22）。
    def create
      result = billing_service.calculate_freight(params[:booking_id].to_s)
      case result.status
      when :ok
        redirect_to billing_invoice_path(result.invoice_number), notice: "料金を算出し請求書を発行しました"
      when :already_invoiced
        redirect_to billing_invoices_path, alert: "この予約は既に請求済みです"
      when :not_found
        redirect_to billing_invoices_path, alert: "予約が見つかりません"
      else
        redirect_to billing_invoices_path, alert: "引取済の予約のみ料金算出できません"
      end
    end

    # 入金を確認し精算を完了する（US23）。
    def confirm
      result = billing_service.settle(params[:id].to_s)
      case result.status
      when :ok
        redirect_to billing_invoice_path(params[:id]), notice: "入金を確認しました"
      when :payment_failed
        redirect_to billing_invoice_path(params[:id]), alert: "入金確認に失敗しました"
      when :booking_sync_failed
        redirect_to billing_invoice_path(params[:id]),
                    alert: "入金は確認しましたが予約の精算同期に失敗しました。予約状態をご確認ください"
      when :invalid
        redirect_to billing_invoice_path(params[:id]), alert: "精算済みの請求書です"
      else
        redirect_to billing_invoices_path, alert: "精算対象の請求書が見つかりません"
      end
    end

    # 料金調整（減額・補償費用）を追加する（US21-6）。担当者・理由を監査証跡として記録する。
    def adjust
      result = billing_service.adjust(
        params[:id].to_s, description: params[:description].to_s,
        amount: params[:amount].to_i, adjustment_type: params[:adjustment_type].to_s,
        adjusted_by: current_user&.username, reason: params[:reason].presence
      )
      case result.status
      when :ok
        redirect_to billing_invoice_path(params[:id]), notice: "料金調整を追加しました"
      when :not_found
        redirect_to billing_invoices_path, alert: "請求書が見つかりません"
      else
        redirect_to billing_invoice_path(params[:id]), alert: "料金調整を追加できません: #{result.error_message}"
      end
    end

    # 料金調整を取り消す（US21-6・T47a）。
    def cancel_adjustment
      result = billing_service.cancel_adjustment(params[:id].to_s, seq_number: params[:seq_number].to_i)
      case result.status
      when :ok
        redirect_to billing_invoice_path(params[:id]), notice: "料金調整を取り消しました"
      when :not_found
        redirect_to billing_invoices_path, alert: "請求書が見つかりません"
      else
        redirect_to billing_invoice_path(params[:id]), alert: "料金調整を取り消せません: #{result.error_message}"
      end
    end

    private

    def billing_service
      @billing_service ||= Billing::Public::BillingService.new
    end
  end
end
