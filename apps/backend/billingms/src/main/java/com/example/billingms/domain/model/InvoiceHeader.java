package com.example.billingms.domain.model;

import java.time.Instant;

/**
 * 請求書の宛名部分（US21-5）。
 *
 * <p><strong>「誰に・どの予約に・いつ出したか」はいつも揃って動く。</strong>
 * 発行した時点で確定し、以後は変わらない——荷主の社名を後から引き直さないのと
 * 同じ理由である（社名を変えた途端に発行済みの請求書の宛名まで変わるのは、
 * 出した書面が後から書き換わるのと同じ）。
 *
 * @param invoiceId 請求番号
 * @param cargoBookingId 予約番号
 * @param shipperId 荷主（法人かどうかを内包する）
 * @param issuedAt 発行日時
 */
public record InvoiceHeader(InvoiceId invoiceId, BillingBookingId cargoBookingId,
        BillingShipperId shipperId, Instant issuedAt) {

    /**
     * 新規に発行するときの検査。
     *
     * <p><strong>復元では検査しない</strong>（新しい不変条件は既存行を壊す）ため、
     * ここは {@link Invoice#issue} からだけ呼ぶ。
     */
    public InvoiceHeader requireComplete() {
        if (invoiceId == null) {
            throw new IllegalArgumentException("請求番号を指定してください");
        }
        if (cargoBookingId == null) {
            throw new IllegalArgumentException("予約を指定してください");
        }
        if (shipperId == null) {
            throw new IllegalArgumentException("荷主を指定してください");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("発行日時を指定してください");
        }
        return this;
    }
}
