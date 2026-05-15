package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/**
 * 見積の識別子（VARCHAR(36) UUID 文字列）。
 *
 * <p>data-model.md L350 {@code quotation.quotation_id} に対応。
 * Axon Event Sourcing の {@code @TargetEntityId} と整合する。</p>
 */
public record QuotationId(String value) {

    public QuotationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("QuotationId は空にできません");
        }
        if (value.length() > 36) {
            throw new IllegalArgumentException("QuotationId は 36 文字以下である必要があります");
        }
    }

    /** 新規 UUID で生成する。 */
    public static QuotationId generate() {
        return new QuotationId(UUID.randomUUID().toString());
    }
}
