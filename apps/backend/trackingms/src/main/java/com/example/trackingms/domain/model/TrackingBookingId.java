package com.example.trackingms.domain.model;

/**
 * 予約番号への論理参照。
 *
 * <p>Booking Context の {@code BookingId} とは別の型にする。DB が分かれており、こちらは
 * 「どの予約の追跡か」を指すだけで、採番も検証もしない。接頭辞を付けているのは、
 * この文脈では予約番号が<strong>追跡の属性</strong>であり、予約そのものの識別子ではないためである。
 *
 * @param value 予約番号の文字列
 */
public record TrackingBookingId(String value) {

    public static TrackingBookingId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("予約番号は必須です");
        }
        return new TrackingBookingId(value);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static TrackingBookingId restore(String value) {
        return value == null ? null : new TrackingBookingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
