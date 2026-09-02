package com.example.routingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 航海番号。Routing Context 固有の識別子であり、共有カーネルには置かない。
 *
 * <p>他コンテキスト（Booking の `Leg` 等）も航海番号を持つが、そちらは「この経路が乗る船便の
 * 呼び名」という参照であり、こちらは「登録された航海そのもの」の識別子である。同じ文字列でも
 * 責務が違うため、型を共有しない。
 */
public final class VoyageNumber {

    private final String value;

    private VoyageNumber(String value) {
        this.value = value;
    }

    /** 新規に受け入れる。ここでだけ検査する。 */
    public static VoyageNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        return new VoyageNumber(value.trim());
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static VoyageNumber restore(String value) {
        return new VoyageNumber(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VoyageNumber number && value.equals(number.value);
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
