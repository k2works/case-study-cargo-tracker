package com.example.cargotracker.bookingms.domain.model.valueobjects;

/**
 * 荷主の識別子（既存 {@code shipper.id} は BIGINT のため Long でラップ）。
 *
 * <p>data-model.md では VARCHAR(36) UUID だが、IT1 実装が BIGINT のため整合を取る。
 * 将来 ADR で同期方針を決める。</p>
 */
public record ShipperId(Long value) {

    public ShipperId {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("ShipperId は正の値である必要があります: " + value);
        }
    }
}
