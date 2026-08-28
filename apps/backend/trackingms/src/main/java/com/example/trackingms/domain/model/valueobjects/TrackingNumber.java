package com.example.trackingms.domain.model.valueobjects;

/**
 * 追跡番号（Tracking Context の業務キー）。
 *
 * <p><strong>Booking Context にも同名の型があるが、共有しない</strong>
 * （{@code VoyageNumber} と同じ扱い。`domain-model.md` のコンテキスト分離設計）。
 * 同じ番号を指していても、向こうは「予約に発行した番号」、こちらは「追跡の識別子」であり、
 * 育つ方向が違う。向こうは発行の可否を持ち、こちらは照会の入口として振る舞う。
 *
 * <p><strong>採番しない。</strong>採番するのは bookingms である（[ADR-021]・[ADR-022] 決定 7）。
 * ここが受け取るのは採番済みの番号であり、形式の検査だけを行う。
 *
 * @param value 番号の文字列
 */
public record TrackingNumber(String value) {

    public static TrackingNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        return new TrackingNumber(value);
    }

        /**
     * 永続化された行から復元する。形式は検査しないが、<strong>空は通さない</strong>。
     *
     * <p>この列は NOT NULL である。空だったなら行が壊れているので、そのまま
     * {@code null} を返すと<strong>呼び出し側からは復元できたように見え</strong>、
     * ずっと先の {@code NullPointerException} として現れる。
     */
    public static TrackingNumber restore(String value) {
        if (value == null) {
            throw new IllegalStateException("追跡番号の無い行を読み込みました");
        }
        return new TrackingNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
