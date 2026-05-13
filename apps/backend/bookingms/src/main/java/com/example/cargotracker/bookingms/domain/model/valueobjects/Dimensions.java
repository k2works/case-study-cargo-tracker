package com.example.cargotracker.bookingms.domain.model.valueobjects;

/**
 * 貨物の寸法（cm）。
 */
public record Dimensions(int lengthCm, int widthCm, int heightCm) {

    public Dimensions {
        if (lengthCm < 0 || widthCm < 0 || heightCm < 0) {
            throw new IllegalArgumentException("Dimensions のいずれかが負値: "
                    + "length=" + lengthCm + " width=" + widthCm + " height=" + heightCm);
        }
    }
}
