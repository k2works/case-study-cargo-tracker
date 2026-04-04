package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingValueObjectsTest {

    @Test
    void shouldCreateValidDimensions() {
        Dimensions dimensions = assertDoesNotThrow(() ->
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("5.0"), new BigDecimal("3.0")));
        assertEquals(new BigDecimal("10.0"), dimensions.length());
        assertEquals(new BigDecimal("5.0"), dimensions.width());
        assertEquals(new BigDecimal("3.0"), dimensions.height());
    }

    @Test
    void shouldThrowWhenDimensionIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new Dimensions(BigDecimal.ZERO, new BigDecimal("5.0"), new BigDecimal("3.0")));
        assertThrows(IllegalArgumentException.class, () ->
                new Dimensions(new BigDecimal("10.0"), new BigDecimal("-1"), new BigDecimal("3.0")));
    }

    @Test
    void shouldCreateValidQuantity() {
        Quantity quantity = assertDoesNotThrow(() -> new Quantity(5));
        assertEquals(5, quantity.value());
    }

    @Test
    void shouldThrowWhenQuantityIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(0));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(-1));
    }

    @Test
    void shouldCreateValidDescription() {
        Description description = assertDoesNotThrow(() -> new Description("電子部品一式"));
        assertEquals("電子部品一式", description.value());
    }

    @Test
    void shouldThrowWhenDescriptionExceedsMaxLength() {
        String longDescription = "a".repeat(501);
        assertThrows(IllegalArgumentException.class, () -> new Description(longDescription));
    }

    @Test
    void shouldAcceptDescriptionAtMaxLength() {
        String maxDescription = "a".repeat(500);
        assertDoesNotThrow(() -> new Description(maxDescription));
    }
}
