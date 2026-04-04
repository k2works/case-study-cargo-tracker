package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

public record Dimensions(BigDecimal length, BigDecimal width, BigDecimal height) {
    public Dimensions {
        if (length == null || length.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("length must be greater than zero");
        }
        if (width == null || width.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("width must be greater than zero");
        }
        if (height == null || height.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("height must be greater than zero");
        }
    }
}
