package com.example.billingms.domain.events;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 精算書発行済イベント（US23、domain-model.md L983）。
 *
 * <p>Invoice 集約が {@code CALCULATED → INVOICED} に遷移したことを表す。集約発火型（ADR-0012）で
 * Read Model 投影で {@code invoice_number} / {@code payment_due} カラムを反映し、cross-service
 * では Notification 経由で荷主にメール通知を送信する（IT7 は LoggingNotificationAcl スタブ）。</p>
 *
 * @param invoiceId     Invoice 識別子
 * @param shipperId     荷主識別子（通知先取得のキー）
 * @param invoiceNumber 採番された請求書番号（INV-YYYYMMDD-XXXX 形式）
 * @param paymentDue    支払期限日（発行日 + 30 日、PaymentDuePolicy 算出）
 * @param totalAmount   請求額（円、参考情報、状態は集約管理）
 * @param issuedAt      発行時刻（Clock 注入で決定的、テスト安定化）
 */
public record InvoiceIssuedEvent(
        String invoiceId,
        String shipperId,
        String invoiceNumber,
        LocalDate paymentDue,
        java.math.BigDecimal totalAmount,
        LocalDateTime issuedAt
) {
}
