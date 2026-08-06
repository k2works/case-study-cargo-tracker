package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecialCargoValueObjectsTest {

    // === HazardousDeclaration のテスト ===

    @Test
    void shouldCreateValidHazardousDeclaration() {
        var declaration = new HazardousDeclaration("3", "UN1203", "ガソリン");

        assertThat(declaration.hazardousClass()).isEqualTo("3");
        assertThat(declaration.unNumber()).isEqualTo("UN1203");
        assertThat(declaration.properShippingName()).isEqualTo("ガソリン");
    }

    @Test
    void shouldThrowWhenHazardousClassIsNull() {
        assertThatThrownBy(() -> new HazardousDeclaration(null, "UN1203", "ガソリン"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenHazardousClassIsBlank() {
        assertThatThrownBy(() -> new HazardousDeclaration("  ", "UN1203", "ガソリン"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenUnNumberIsNull() {
        assertThatThrownBy(() -> new HazardousDeclaration("3", null, "ガソリン"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenUnNumberIsBlank() {
        assertThatThrownBy(() -> new HazardousDeclaration("3", "  ", "ガソリン"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenUnNumberFormatIsInvalid() {
        assertThatThrownBy(() -> new HazardousDeclaration("3", "1203", "ガソリン"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenProperShippingNameIsNull() {
        assertThatThrownBy(() -> new HazardousDeclaration("3", "UN1203", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenProperShippingNameIsBlank() {
        assertThatThrownBy(() -> new HazardousDeclaration("3", "UN1203", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // === TemperatureUnit のテスト ===

    @Test
    void shouldHaveCorrectTemperatureUnits() {
        assertThat(TemperatureUnit.CELSIUS).isNotNull();
        assertThat(TemperatureUnit.FAHRENHEIT).isNotNull();
        assertThat(TemperatureUnit.values()).hasSize(2);
    }

    // === TemperatureRequirement のテスト ===

    @Test
    void shouldCreateValidTemperatureRequirement() {
        var requirement = new TemperatureRequirement(
                new BigDecimal("-25"), new BigDecimal("-18"), TemperatureUnit.CELSIUS);

        assertThat(requirement.minTemperature()).isEqualTo(new BigDecimal("-25"));
        assertThat(requirement.maxTemperature()).isEqualTo(new BigDecimal("-18"));
        assertThat(requirement.unit()).isEqualTo(TemperatureUnit.CELSIUS);
    }

    @Test
    void shouldThrowWhenMinTemperatureIsNull() {
        BigDecimal maxTemperature = new BigDecimal("-18");
        assertThatThrownBy(() -> new TemperatureRequirement(
                null, maxTemperature, TemperatureUnit.CELSIUS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenMaxTemperatureIsNull() {
        BigDecimal minTemperature = new BigDecimal("-25");
        assertThatThrownBy(() -> new TemperatureRequirement(
                minTemperature, null, TemperatureUnit.CELSIUS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenMinExceedsMax() {
        BigDecimal minTemperature = new BigDecimal("10");
        BigDecimal maxTemperature = new BigDecimal("5");
        assertThatThrownBy(() -> new TemperatureRequirement(
                minTemperature, maxTemperature, TemperatureUnit.CELSIUS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenUnitIsNull() {
        BigDecimal minTemperature = new BigDecimal("-25");
        BigDecimal maxTemperature = new BigDecimal("-18");
        assertThatThrownBy(() -> new TemperatureRequirement(
                minTemperature, maxTemperature, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowEqualMinAndMax() {
        var requirement = new TemperatureRequirement(
                new BigDecimal("-20"), new BigDecimal("-20"), TemperatureUnit.CELSIUS);

        assertThat(requirement.minTemperature()).isEqualTo(new BigDecimal("-20"));
        assertThat(requirement.maxTemperature()).isEqualTo(new BigDecimal("-20"));
    }
}
