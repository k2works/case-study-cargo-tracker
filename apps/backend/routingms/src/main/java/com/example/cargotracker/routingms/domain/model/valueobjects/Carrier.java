package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 運送会社情報。
 *
 * @param code 運送会社コード（VARCHAR(10)）
 * @param name 運送会社名（VARCHAR(200)）
 */
public record Carrier(String code, String name) {

    public Carrier {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (code.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Carrier の code / name は必須です");
        }
        if (code.length() > 10) {
            throw new IllegalArgumentException("Carrier コードは 10 文字以下である必要があります: " + code);
        }
    }
}
