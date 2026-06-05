package com.example.billingms.domain.events;

import java.time.LocalDateTime;

/**
 * 支払期限超過イベント（US23、domain-model.md L985）。
 *
 * <p>Invoice 集約が {@code INVOICED → OVERDUE} に遷移したことを表す。集約発火型（ADR-0012）で
 * Read Model 投影で {@code billing_status} を OVERDUE に更新し、cross-service では Notification
 * 経由で経理担当者・荷主に督促通知を送信する（IT7 は LoggingNotificationAcl スタブ）。</p>
 *
 * @param invoiceId  Invoice 識別子
 * @param shipperId  荷主識別子（通知先取得のキー）
 * @param markedAt   遷移時刻（Clock 注入で決定的、Scheduler 実行時刻）
 */
public record InvoiceOverdueEvent(
        String invoiceId,
        String shipperId,
        LocalDateTime markedAt
) {
}
