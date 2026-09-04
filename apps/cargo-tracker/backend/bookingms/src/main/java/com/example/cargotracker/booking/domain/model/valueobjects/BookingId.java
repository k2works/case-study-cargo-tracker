package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
/**
 * 予約の識別子。
 *
 * <p>共有カーネルには置かない。同じ予約を指す識別子が BC の数だけあるのは
 * 重複ではなく、境界を分けた代金である（domain-model.md「共有カーネルの範囲」）。</p>
 */
public record BookingId(String value) {

    public BookingId {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("予約 ID は必須です");
        }
    }
}
