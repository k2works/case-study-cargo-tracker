package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

/** 重量（kg）。料金計算の入力になるので 0 は認めない。 */
public record Weight(BigDecimal kilograms) {

    public Weight {
        if (kilograms == null || kilograms.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値です: " + kilograms);
        }
    }

    public static Weight ofKilograms(String value) {
        return new Weight(new BigDecimal(value));
    }
}
