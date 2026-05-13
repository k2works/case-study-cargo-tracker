package com.example.cargotracker.authms.domain.model;

import java.util.UUID;

public record UserId(String value) {

    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId は必須です");
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID().toString());
    }
}
