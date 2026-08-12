package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 貨物の品名。<strong>オプション項目</strong>。
 *
 * @param value 品名（前後の空白を除いて 1〜500 文字）
 */
public record Description(String value) {

    private static final int MAX_LENGTH = 500;

    public Description {
        if (value == null) {
            throw new IllegalArgumentException("品名は必須です");
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("品名が空です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("品名は " + MAX_LENGTH + " 文字までです: " + value.length());
        }
    }

    public static Description of(String value) {
        return new Description(value);
    }

    /**
     * 未入力を許して生成する。空白のみの入力は品名なしとして扱う。
     *
     * @return 品名。未入力または空白のみなら {@code null}
     */
    public static Description ofNullable(String value) {
        return value == null || value.isBlank() ? null : of(value);
    }
}
