package com.example.cargotracker.authms.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("メールアドレスは必須です");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + value);
        }
    }
}
