package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackingNumberTest {

    @Test
    void shouldGenerateTrackingNumber() {
        TrackingNumber trackingNumber = TrackingNumber.generate();
        assertNotNull(trackingNumber);
        assertNotNull(trackingNumber.value());
    }

    @Test
    void shouldGenerateUniqueTrackingNumbers() {
        TrackingNumber first = TrackingNumber.generate();
        TrackingNumber second = TrackingNumber.generate();
        assertNotEquals(first.value(), second.value());
    }

    @Test
    void shouldGenerateTrackingNumberWithExpectedFormat() {
        TrackingNumber trackingNumber = TrackingNumber.generate();
        assertTrue(trackingNumber.value().matches("TRK-\\d{8}-[A-F0-9]{8}"),
                "Expected format TRK-YYYYMMDD-XXXXXXXX but was: " + trackingNumber.value());
    }

    @Test
    void shouldCreateTrackingNumberFromValue() {
        TrackingNumber trackingNumber = TrackingNumber.of("TRK-20260417-ABCD1234");
        assertEquals("TRK-20260417-ABCD1234", trackingNumber.value());
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        assertThrows(IllegalArgumentException.class, () -> TrackingNumber.of(null));
    }

    @Test
    void shouldThrowWhenValueIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> TrackingNumber.of(""));
    }

    @Test
    void shouldReturnValueFromToString() {
        TrackingNumber trackingNumber = TrackingNumber.of("TRK-20260417-ABCD1234");
        assertEquals("TRK-20260417-ABCD1234", trackingNumber.toString());
    }
}
