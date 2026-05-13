package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 航海番号（VARCHAR(20) 自然キー）。
 *
 * <p>data-model.md「voyage_number は自然キー（業界の運用上の値）」に準拠。
 * 大文字英数 + ハイフンを許容。</p>
 */
public record VoyageNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9-]{1,20}$");

    public VoyageNumber {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("VoyageNumber は 1-20 文字の大文字英数・ハイフンである必要があります: " + value);
        }
    }
}
