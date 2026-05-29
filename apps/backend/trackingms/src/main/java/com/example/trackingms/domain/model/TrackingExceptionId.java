package com.example.trackingms.domain.model;

import java.util.UUID;

/**
 * 追跡例外識別子（US19 / US20 / domain-model.md）。
 *
 * <p>{@link TrackingActivity} 集約スコープの識別子（同一追跡番号内で一意）。
 * 値は UUID 文字列で、シリアル化・URL 埋め込みに耐える。</p>
 */
public record TrackingExceptionId(String value) {

    public TrackingExceptionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TrackingExceptionId は null または空にできません");
        }
    }

    public static TrackingExceptionId generate() {
        return new TrackingExceptionId(UUID.randomUUID().toString());
    }
}
