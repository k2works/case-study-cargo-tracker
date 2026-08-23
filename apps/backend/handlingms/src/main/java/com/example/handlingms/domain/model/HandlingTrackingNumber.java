package com.example.handlingms.domain.model;

/**
 * 追跡番号（Handling Context 固有の型）。
 *
 * <p>US15-1 は追跡番号を作業の起点にする。荷役作業員は予約番号を知らない。
 *
 * <p>Tracking Context の {@code TrackingNumber} とは別の型にする。こちらは
 * <strong>貨物を引くための入力</strong>であり、採番も検証もしない。書式まで検査すると、
 * 採番の規則が 2 か所に分かれる（[ADR-011] と同じ理由）。
 *
 * @param value 追跡番号の文字列
 */
public record HandlingTrackingNumber(String value) {

    public static HandlingTrackingNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        return new HandlingTrackingNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
