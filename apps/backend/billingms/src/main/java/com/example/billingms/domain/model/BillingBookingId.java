package com.example.billingms.domain.model;

/**
 * 予約参照 ID（論理参照）。
 *
 * <p><strong>bookingms の {@code BookingId} とは別の型である。</strong>こちらが知っているのは
 * 「どの予約に対する請求か」だけで、予約の中身は知らない。相手の型を持ち込むと、
 * bookingms の変更がこちらのコンパイルを壊す。
 *
 * @param value 予約番号
 */
public record BillingBookingId(String value) {

    public BillingBookingId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("予約番号を指定してください");
        }
    }

    public static BillingBookingId of(String value) {
        return new BillingBookingId(value);
    }
}
