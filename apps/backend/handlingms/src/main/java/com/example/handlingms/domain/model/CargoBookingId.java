package com.example.handlingms.domain.model;

/**
 * 予約番号への論理参照（[ADR-023] 決定 2）。
 *
 * <p>Booking Context の {@code BookingId} とは別の型である。DB が分かれており、こちらは
 * 「どの予約の荷役か」を指すだけで、採番も検証もしない。
 *
 * <p>名前は[ドメインモデル](../../../../../../../docs/design/domain-model.md)に合わせている。
 * Tracking Context の {@code TrackingBookingId} と付け方が揃っていないが、そろえるかどうかは
 * 型名の変更を伴うため、まとめて決める（[ADR-023] のコンテキスト）。
 *
 * @param value 予約番号の文字列
 */
public record CargoBookingId(String value) {

    public static CargoBookingId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("予約番号は必須です");
        }
        return new CargoBookingId(value);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static CargoBookingId restore(String value) {
        return value == null ? null : new CargoBookingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
