package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 貨物の重量（キログラム）。
 *
 * <p>{@code double} ではなく {@link BigDecimal} を使う。重量は請求額の算定根拠であり、
 * 二進浮動小数の丸め誤差が金額に伝播する。
 *
 * <p>小数第 3 位までに制限するのは DB が {@code NUMERIC(10,3)} だからである。
 * **ドメインで許して DB で落とすと、業務のことばで説明できないエラーが利用者に届く。**
 *
 * @param kilograms 重量（kg。正の値、小数第 3 位まで）
 */
public record Weight(BigDecimal kilograms) {

    private static final int SCALE = 3;

    public Weight {
        if (kilograms == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        if (kilograms.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値です: " + kilograms);
        }
        if (kilograms.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("重量は小数第 3 位までです: " + kilograms);
        }
    }

    public static Weight ofKilograms(BigDecimal kilograms) {
        return new Weight(kilograms);
    }
}
