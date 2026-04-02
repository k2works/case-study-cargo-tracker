package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.util.concurrent.ThreadLocalRandom;

public record TrackingNumber(String value) {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 8;

    public TrackingNumber {
        if (value == null || !value.matches("TRK-[A-Z0-9]{8}")) {
            throw new IllegalArgumentException("無効な追跡番号フォーマット: " + value);
        }
    }

    public static TrackingNumber generate() {
        StringBuilder sb = new StringBuilder("TRK-");
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        return new TrackingNumber(sb.toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
