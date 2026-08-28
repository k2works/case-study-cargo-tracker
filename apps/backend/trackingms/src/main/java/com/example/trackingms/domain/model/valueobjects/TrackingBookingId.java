package com.example.trackingms.domain.model.valueobjects;

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

        /**
     * 永続化された行から復元する。形式は検査しないが、<strong>空は通さない</strong>。
     *
     * <p>この列は NOT NULL である。空だったなら行が壊れているので、そのまま
     * {@code null} を返すと<strong>呼び出し側からは復元できたように見え</strong>、
     * ずっと先の {@code NullPointerException} として現れる。
     */
    public static TrackingBookingId restore(String value) {
        if (value == null) {
            throw new IllegalStateException("予約番号の無い行を読み込みました");
        }
        return new TrackingBookingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
