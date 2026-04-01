package com.example.cargotracker.quote.domain.model.valueobjects;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 発行された見積番号を表す値オブジェクト。
 *
 * <p>形式: {@code Q-YYYYMMDD-XXXX}（XXXX は UUID から導出した4桁大文字英数字）
 */
public final class QuoteNumber {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final String value;

    private QuoteNumber(String value) {
        this.value = value;
    }

    /**
     * 現在日時と UUID から見積番号を生成する。
     */
    public static QuoteNumber generate(UUID seed) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String suffix = seed.toString().replace("-", "").substring(0, 4).toUpperCase();
        return new QuoteNumber("Q-" + datePart + "-" + suffix);
    }

    /**
     * 永続化ストアから再構成する。
     */
    public static QuoteNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("見積番号は null または空にできません");
        }
        return new QuoteNumber(value);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteNumber that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
