package com.example.billingms.application.outboundservices.notification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 荷主・経理担当者への通知 Port（US23、IT7 T4.4、domain-model.md M7）。
 *
 * <p>IT7 はログ出力スタブとして実装する（{@code LoggingNotificationAcl}）。実メール送信は
 * IT8 で SendGrid 統合（ADR-0018）に切替予定。</p>
 *
 * <p>US23 の受入基準 2「精算書が荷主にメール通知される」/ 受入基準 4「入金確認」/
 * 受入基準 5「督促通知」をスタブ呼び出しで満たす設計。</p>
 */
public interface NotificationAcl {

    /**
     * 精算書発行を荷主に通知する（US23 受入基準 2）。
     *
     * @param invoiceId     Invoice 識別子
     * @param shipperId     荷主識別子（通知先取得のキー）
     * @param invoiceNumber 採番された請求書番号（INV-YYYYMMDD-XXXX）
     * @param paymentDue    支払期限
     * @param totalAmount   請求額
     */
    void notifyInvoiceIssued(String invoiceId,
                             String shipperId,
                             String invoiceNumber,
                             LocalDate paymentDue,
                             BigDecimal totalAmount);

    /**
     * 入金記録を荷主・経理担当者に通知する（US23 受入基準 4）。
     *
     * @param invoiceId    Invoice 識別子
     * @param shipperId    荷主識別子
     * @param paymentId    入金識別子
     * @param paidAmount   入金額
     */
    void notifyPaymentReceived(String invoiceId,
                               String shipperId,
                               String paymentId,
                               BigDecimal paidAmount);

    /**
     * 支払期限超過を荷主・経理担当者に通知する（US23 受入基準 5）。
     *
     * @param invoiceId Invoice 識別子
     * @param shipperId 荷主識別子
     */
    void notifyOverdue(String invoiceId, String shipperId);
}
