package com.example.cargotracker.shared.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Location {

    private static final Pattern UNLOCODE_PATTERN = Pattern.compile("^[A-Z0-9]{5}$");

    private final String unlocode;

    public Location(String unlocode) {
        if (unlocode == null || unlocode.isBlank()) {
            throw new IllegalArgumentException("unlocode must not be blank");
        }
        if (!UNLOCODE_PATTERN.matcher(unlocode).matches()) {
            throw new IllegalArgumentException("unlocode must be 5 uppercase alphanumeric characters");
        }
        this.unlocode = unlocode;
    }

    public String getUnlocode() {
        return unlocode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Location location)) {
            return false;
        }
        return Objects.equals(unlocode, location.unlocode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unlocode);
    }

    @Override
    public String toString() {
        return unlocode;
    }
}
