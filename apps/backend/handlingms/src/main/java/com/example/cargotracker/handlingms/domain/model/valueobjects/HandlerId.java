package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 荷役作業員 ID を表す値オブジェクト。
 *
 * <p>handlingms 内では認証コンテキストから受け取った文字列 ID を保持する。
 * 認証コンテキスト（authms）と疎結合に保つため、ここでは UUID 形式の検証は行わない。</p>
 */
public record HandlerId(String value) {

    public HandlerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("HandlerId は空文字にできません");
        }
    }
}
