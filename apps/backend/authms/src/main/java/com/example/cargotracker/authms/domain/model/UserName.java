package com.example.cargotracker.authms.domain.model;

public record UserName(String value) {

    public UserName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ユーザー名は必須です");
        }
        if (value.length() < 3) {
            throw new IllegalArgumentException("ユーザー名は 3 文字以上必要です");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("ユーザー名は 50 文字以内である必要があります");
        }
    }
}
