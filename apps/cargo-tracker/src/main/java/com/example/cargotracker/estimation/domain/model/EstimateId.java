package com.example.cargotracker.estimation.domain.model;

import java.util.UUID;

/**
 * 見積 ID（US01 の受入基準 4「見積番号が発行される」）。
 *
 * @param value UUID
 */
public record EstimateId(UUID value) {

    public EstimateId {
        if (value == null) {
            throw new IllegalArgumentException("見積 ID は必須です");
        }
    }

    /** 新しい見積 ID を採番する。 */
    public static EstimateId generate() {
        return new EstimateId(UUID.randomUUID());
    }

    /** 文字列から復元する。 */
    public static EstimateId of(String value) {
        return new EstimateId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
