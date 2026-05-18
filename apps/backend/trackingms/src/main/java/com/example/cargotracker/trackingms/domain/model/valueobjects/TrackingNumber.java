package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡番号を表す値オブジェクト。
 *
 * <p>形式: {@code TRK-YYYYMMDD-XXXXXXXX}（IT4 bookingms 実装と整合・例: {@code TRK-20260518-3053F0B8}）。
 * trackingms は bookingms から {@code CargoTrackedEvent} 経由で受け取るため、
 * ここでは最小限の検証のみ行う（handlingms と同じ方針）。</p>
 *
 * <p>data-model.md には「{@code TRK-} + 大文字英数 10 桁」と記載されているが、
 * bookingms の実際の生成ロジック {@code "TRK-" + YYYYMMDD + "-" + UUID 前 8 桁} と
 * 整合しないため、サービス間で受け渡されるフォーマットを正としている。</p>
 */
public record TrackingNumber(String value) {

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TrackingNumber は空文字にできません");
        }
        if (!value.startsWith("TRK-")) {
            throw new IllegalArgumentException("TrackingNumber は 'TRK-' で始まる必要があります: " + value);
        }
        if (value.length() > 25) {
            throw new IllegalArgumentException("TrackingNumber は 25 文字以内である必要があります: " + value);
        }
    }
}
