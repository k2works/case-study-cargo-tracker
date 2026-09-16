package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorporateContractTest {

    private static final DiscountRate RATE = new DiscountRate(new BigDecimal("0.1000"));

    @Test
    @DisplayName("契約番号と割引率が揃っていれば作れる")
    void acceptsComplete() {
        assertThatCode(() -> new CorporateContract("CT-0001", RATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("契約番号が空なら作れない")
    void rejectsBlankContractNumber() {
        assertThatThrownBy(() -> new CorporateContract("  ", RATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("契約番号");
    }

    @Test
    @DisplayName("契約番号が null なら作れない")
    void rejectsNullContractNumber() {
        assertThatThrownBy(() -> new CorporateContract(null, RATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("割引率が無ければ作れない")
    void rejectsNullDiscountRate() {
        assertThatThrownBy(() -> new CorporateContract("CT-0001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("割引率");
    }
}
