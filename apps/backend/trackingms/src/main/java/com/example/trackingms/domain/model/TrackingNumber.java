package com.example.trackingms.domain.model;

import java.util.regex.Pattern;

/**
 * 追跡番号の値オブジェクト（US14）。
 *
 * <p>荷主に共有される識別子のため、推測困難な書式
 * <code>TRK-</code> + 大文字英数 10 桁を厳格に検証する。
 * 採番ロジックは {@code TrackingNumberGenerator} ドメインサービスに分離する。</p>
 *
 * @param value 追跡番号文字列（例: {@code TRK-AB12CD3456}）
 */
public record TrackingNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^TRK-[A-Z0-9]{10}$");

    public TrackingNumber {
        if (value == null) {
            throw new IllegalArgumentException("追跡番号は null にできません");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "追跡番号は「TRK-」+ 大文字英数 10 桁の書式である必要があります: " + value);
        }
    }

    /**
     * 静的ファクトリ。検証は record コンストラクタが行う。
     */
    public static TrackingNumber of(String value) {
        return new TrackingNumber(value);
    }
}
