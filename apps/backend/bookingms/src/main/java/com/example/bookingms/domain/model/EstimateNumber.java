package com.example.bookingms.domain.model;

/**
 * 見積番号（US01-4・[ADR-028] 決定 7）。
 *
 * <p><strong>人が読む番号である。</strong>受入基準 01-4 は「見積番号が発行される」と
 * 書いており、荷主と電話で読み合わせる番号が要る——UUID を読み上げることはできない。
 *
 * <p>形は {@code EST-YYYY} + 6 桁（[ADR-011] の請求番号と同じ形）。
 * <strong>年は業務の暦で決まる。</strong>組み立てと採番は DB のシーケンスに任せる
 * ——アプリ側で作ると、別の経路が違う形式を発行できてしまう。
 *
 * @param value 見積番号
 */
public record EstimateNumber(String value) {

    /** {@code EST-2026000001} の形。 */
    private static final java.util.regex.Pattern PATTERN =
            java.util.regex.Pattern.compile("^EST-\\d{10}$");

    public EstimateNumber {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("見積番号の形式が違います: " + value);
        }
    }

    public static EstimateNumber of(String value) {
        return new EstimateNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
