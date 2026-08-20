package com.example.bookingms.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 予約番号（ADR-011）。
 *
 * <p>5 サービスが論理参照するキーであり、外部キー制約が張れない。値そのものが契約になるため、
 * 形式をここで固定する。採番は DB のシーケンスが行い、この型は受け取った値を検査するだけ。
 * 集約側で組み立てると、シーケンスと衝突した番号を発行できてしまう。
 */
public final class BookingId {

    /** `BKG-` + 西暦 4 桁 + 連番 6 桁。 */
    private static final Pattern PATTERN = Pattern.compile("^BKG-\\d{4}\\d{6}$");

    private final String value;

    private BookingId(String value) {
        this.value = value;
    }

    public static BookingId of(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("予約番号の形式が不正です: " + value);
        }
        return new BookingId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BookingId bookingId && value.equals(bookingId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
