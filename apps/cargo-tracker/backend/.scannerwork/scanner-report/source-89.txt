package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
/** 荷主の識別子（Booking Context 側の型）。 */
public record ShipperId(String value) {

    public ShipperId {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation("荷主 ID は必須です");
        }
    }
}
