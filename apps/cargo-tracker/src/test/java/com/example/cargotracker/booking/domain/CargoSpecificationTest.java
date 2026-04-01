package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CargoSpecification 値オブジェクト")
class CargoSpecificationTest {

    @Test
    @DisplayName("重量が 0 以下の場合は例外を投げる")
    void rejectNonPositiveWeight() {
        assertThatThrownBy(() -> new CargoSpecification(
                CargoType.GENERAL_CARGO,
                BigDecimal.ZERO,
                null, null, null,
                1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("重量が負の場合も例外を投げる")
    void rejectNegativeWeight() {
        assertThatThrownBy(() -> new CargoSpecification(
                CargoType.GENERAL_CARGO,
                new BigDecimal("-1.0"),
                null, null, null,
                1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("個数が 0 の場合は例外を投げる")
    void rejectZeroQuantity() {
        assertThatThrownBy(() -> new CargoSpecification(
                CargoType.GENERAL_CARGO,
                new BigDecimal("10.0"),
                null, null, null,
                0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("個数");
    }

    @Test
    @DisplayName("個数が負の場合も例外を投げる")
    void rejectNegativeQuantity() {
        assertThatThrownBy(() -> new CargoSpecification(
                CargoType.GENERAL_CARGO,
                new BigDecimal("10.0"),
                null, null, null,
                -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidSpec() {
        CargoSpecification spec = new CargoSpecification(
                CargoType.REFRIGERATED,
                new BigDecimal("50.5"),
                new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("60"),
                3, "冷凍食品");

        assertThat(spec.cargoType()).isEqualTo(CargoType.REFRIGERATED);
        assertThat(spec.weightKg()).isEqualByComparingTo("50.5");
        assertThat(spec.quantity()).isEqualTo(3);
        assertThat(spec.description()).isEqualTo("冷凍食品");
    }
}
