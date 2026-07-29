# frozen_string_literal: true

namespace :billing do
  desc "支払期限を超過した PENDING 請求書を OVERDUE にし経理へ未払い通知する（US23-5・cron から定期実行）"
  task mark_overdue: :environment do
    Booking::Public::NotificationWiring.install! # invoice_overdue 購読を結線
    result = Billing::Public::BillingService.new.mark_overdue
    puts "[billing:mark_overdue] #{result.overdue_count} 件を OVERDUE にし未払い通知を送信しました"
  end
end
