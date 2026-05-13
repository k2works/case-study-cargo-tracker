package com.example.cargotracker.authms.domain.model;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("パスワードハッシュは必須です");
        }
    }
}
