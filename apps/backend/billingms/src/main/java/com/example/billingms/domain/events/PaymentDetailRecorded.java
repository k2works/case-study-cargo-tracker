package com.example.billingms.domain.events;

/**
 * 入金詳細記録イベント（IT8 T5.1 / ADR-0019、shared {@code PaymentRecordedEvent} の補完）。
 *
 * <p>billingms 内部の決済方法 / 取引番号を永続化する。cross-service 契約には含めない
 * （shared event は ADR-0012 集約発火型に準拠した最小契約として維持）。</p>
 *
 * <p>{@code InvoiceProjection} の {@code @EventHandler} が本 event を受信し、
 * {@code payment} テーブルの {@code payment_method} / {@code external_reference} 列を更新する。
 * shared {@code PaymentRecordedEvent} と同じ {@code paymentId} で関連付け、後段 UPDATE で値を反映する。</p>
 *
 * @param invoiceId          Invoice 識別子
 * @param paymentId          Payment 識別子（shared event と同一値）
 * @param paymentMethod      支払方法（BANK_TRANSFER / CREDIT_CARD / MANUAL、null 可）
 * @param externalReference  決済機関の取引番号（任意、IT8 ADR-0020 で webhook 受信時に設定）
 */
public record PaymentDetailRecorded(
        String invoiceId,
        String paymentId,
        String paymentMethod,
        String externalReference
) {

    /**
     * IT8 レビュー M4（IT9 A1.4 統合）: コンストラクタでの二重防御。
     * RecordPaymentCommand / RecordPartialPaymentCommand 側でも検証されるが、
     * 受信側（投影）でも record の不変条件を保つ。
     */
    public PaymentDetailRecorded {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("invoiceId は必須です");
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId は必須です");
        }
        // paymentMethod / externalReference は両方 null 可能だが、両方 null なら本 event 自体不要
        if ((paymentMethod == null || paymentMethod.isBlank())
                && (externalReference == null || externalReference.isBlank())) {
            throw new IllegalArgumentException(
                    "paymentMethod または externalReference のいずれかが必須です");
        }
    }
}
