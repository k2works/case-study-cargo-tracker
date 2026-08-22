package com.example.bookingms.domain.model;

import java.util.regex.Pattern;

/**
 * 追跡番号（US14・[ADR-021]）。
 *
 * <p>形式は {@code TRK-yyyyMMdd-nnnn}。日付を含めるのは、問い合わせを受けたときに
 * いつごろの貨物かが番号だけで分かるためである。
 *
 * <p><strong>ここでは組み立てない</strong>（[ADR-011] と同じ形）。採番は永続化の経路
 * （DB のシーケンス）が行う。集約で文字列を作ると、別の入口が違う形式を発行できてしまい、
 * サービスをまたいだ照合が壊れる。ここが持つのは<strong>形式の不変条件だけ</strong>である。
 *
 * <p>Tracking Context にも同名の型がある。<strong>共有しない</strong>——同じ番号を指していても、
 * こちらは「予約に発行した番号」、向こうは「追跡の識別子」であり、育つ方向が違う
 * （{@code VoyageNumber} と同じ扱い）。
 *
 * @param value 番号の文字列
 */
public record TrackingNumber(String value) {

    /** {@code data-model.md} の {@code VARCHAR(20)} に収まる長さである（17 文字）。 */
    private static final Pattern PATTERN = Pattern.compile("^TRK-\\d{8}-\\d{4}$");

    /** 新しく受け入れる。ここで検査する。 */
    public static TrackingNumber of(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("追跡番号の形式が不正です: " + value);
        }
        return new TrackingNumber(value);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない（[ADR-012]）。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    public static TrackingNumber restore(String value) {
        return value == null ? null : new TrackingNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
