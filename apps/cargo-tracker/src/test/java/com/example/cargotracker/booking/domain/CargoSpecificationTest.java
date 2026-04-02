package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CargoSpecification 値オブジェクト")
class CargoSpecificationTest {

    @Test
    @DisplayName("重量が 0 以下の場合は例外を投げる")
    void rejectNonPositiveWeight() {
        assertThatThrownBy(() -> createCargoSpecification(BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("重量が負の場合も例外を投げる")
    void rejectNegativeWeight() {
        assertThatThrownBy(() -> createCargoSpecification(new BigDecimal("-1.0"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("個数が 0 の場合は例外を投げる")
    void rejectZeroQuantity() {
        assertThatThrownBy(() -> createCargoSpecification(new BigDecimal("10.0"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("個数");
    }

    @Test
    @DisplayName("個数が負の場合も例外を投げる")
    void rejectNegativeQuantity() {
        assertThatThrownBy(() -> createCargoSpecification(new BigDecimal("10.0"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidSpec() {
        CargoSpecification spec = new CargoSpecification(
                CargoType.REFRIGERATED,
                new BigDecimal("50.5"),
                new BigDecimal("100"),
                new BigDecimal("80"),
                new BigDecimal("60"),
                3,
                "冷凍食品"
        );

        assertThat(spec.cargoType()).isEqualTo(CargoType.REFRIGERATED);
        assertThat(spec.weightKg()).isEqualByComparingTo("50.5");
        assertThat(spec.quantity()).isEqualTo(3);
        assertThat(spec.description()).isEqualTo("冷凍食品");
    }

    private CargoSpecification createCargoSpecification(BigDecimal weightKg, int quantity) {
        return new CargoSpecification(
                CargoType.GENERAL_CARGO,
                weightKg,
                null,
                null,
                null,
                quantity,
                null
        );
    }
}
