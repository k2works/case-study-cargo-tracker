package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 貨物の個数。<strong>オプション項目</strong>。
 *
 * @param value 個数（1 以上）
 */
public record Quantity(int value) {

    public Quantity {
        if (value < 1) {
            throw new IllegalArgumentException("個数は 1 以上です: " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    /**
     * 未入力を許して生成する。
     *
     * @return 個数。未入力なら {@code null}
     */
    public static Quantity ofNullable(Integer value) {
        return value == null ? null : of(value);
    }
}
