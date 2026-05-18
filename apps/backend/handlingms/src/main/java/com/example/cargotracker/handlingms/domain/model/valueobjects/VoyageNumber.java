package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 航海番号を表す値オブジェクト。
 *
 * <p>routingms が管理する Voyage の自然キーを参照する。
 * handlingms は ACL（CargoSnapshot）経由で取得するため、ここでは形式検証のみ行う。</p>
 */
public record VoyageNumber(String value) {

    public VoyageNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("VoyageNumber は空文字にできません");
        }
        if (value.length() > 20) {
            throw new IllegalArgumentException("VoyageNumber は 20 文字以内である必要があります: " + value);
        }
    }
}
