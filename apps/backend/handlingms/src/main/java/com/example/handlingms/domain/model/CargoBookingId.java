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

        /**
     * 永続化された行から復元する。形式は検査しないが、<strong>空は通さない</strong>。
     *
     * <p>この列は NOT NULL である。空だったなら行が壊れているので、そのまま
     * {@code null} を返すと<strong>呼び出し側からは復元できたように見え</strong>、
     * ずっと先の {@code NullPointerException} として現れる。
     */
    public static CargoBookingId restore(String value) {
        if (value == null) {
            throw new IllegalStateException("予約番号の無い行を読み込みました");
        }
        return new CargoBookingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
