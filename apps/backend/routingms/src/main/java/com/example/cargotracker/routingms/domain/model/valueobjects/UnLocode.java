package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * UN/LOCODE（国連の港湾コード、5 文字）。
 *
 * <p>将来 shared モジュールに昇格する候補。現状は routingms / bookingms 内に
 * 個別配置している。</p>
 */
public record UnLocode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{3}$");

    public UnLocode {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("UnLocode は 5 文字の英数（最初 2 文字は国コード）である必要があります: " + value);
        }
    }
}
