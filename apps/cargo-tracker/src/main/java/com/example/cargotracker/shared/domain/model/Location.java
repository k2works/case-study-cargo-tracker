package com.example.cargotracker.shared.domain.model;

import java.util.regex.Pattern;

public record Location(String unlocode) {

    private static final Pattern UNLOCODE_PATTERN = Pattern.compile("^[A-Z0-9]{5}$");

    public Location {
        if (unlocode == null || unlocode.isBlank()) {
            throw new IllegalArgumentException("unlocode must not be blank");
        }
        if (!UNLOCODE_PATTERN.matcher(unlocode).matches()) {
            throw new IllegalArgumentException("unlocode must be 5 uppercase alphanumeric characters");
        }
    }

    @Override
    public String toString() {
        return unlocode;
    }
}
