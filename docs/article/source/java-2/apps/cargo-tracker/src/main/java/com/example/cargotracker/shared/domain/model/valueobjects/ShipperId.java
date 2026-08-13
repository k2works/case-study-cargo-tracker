package com.example.cargotracker.shared.domain.model.valueobjects;

import java.util.UUID;

/**
 * 荷主識別子。<strong>共有カーネル</strong>（ADR-005）。
 *
 * <p>共有カーネルに置いてよいのは {@code Location} と本クラスの 2 要素のみである。
 * 識別子は値としての同一性のみを持ち、業務的な振る舞いを持たないため、
 * BC 間で共有するコストが極めて低い。
 *
 * @param value UUID
 */
public record ShipperId(UUID value) {

    public ShipperId {
        if (value == null) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
    }

    public static ShipperId generate() {
        return new ShipperId(UUID.randomUUID());
    }

    public static ShipperId of(String value) {
        return new ShipperId(UUID.fromString(value));
    }
}
