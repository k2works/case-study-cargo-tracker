package com.example.billingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CorporateContract} の不変条件検証（US22 / IT7 タスク 3.1）。
 *
 * <p>荷主契約。荷主種別と契約割引率を保持し、{@link CorporateDiscountPolicy} に渡される。
 * domain-model.md（IT7 改訂版）の Billing Context の補完値オブジェクト。</p>
 */
class CorporateContractTest {

    @Test
    @DisplayName("US22: CORPORATE 荷主は 0〜30% の割引率を保持できる")
    void CORPORATE荷主の正常値() {
        CorporateContract contract = new CorporateContract(
                "S-001", ShipperType.CORPORATE, new BigDecimal("0.15"));

        assertThat(contract.shipperId()).isEqualTo("S-001");
        assertThat(contract.shipperType()).isEqualTo(ShipperType.CORPORATE);
        assertThat(contract.discountRate()).isEqualByComparingTo("0.15");
    }

    @Test
    @DisplayName("US22: INDIVIDUAL 荷主は割引率 0 で保持される")
    void INDIVIDUAL荷主の正常値() {
        CorporateContract contract = new CorporateContract(
                "S-002", ShipperType.INDIVIDUAL, BigDecimal.ZERO);

        assertThat(contract.shipperType()).isEqualTo(ShipperType.INDIVIDUAL);
        assertThat(contract.discountRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("US22: 割引率の上限境界値 0.30 を受理する")
    void 割引率上限の境界値() {
        CorporateContract contract = new CorporateContract(
                "S-003", ShipperType.CORPORATE, new BigDecimal("0.30"));

        assertThat(contract.discountRate()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("US22: 割引率 30% 超は IllegalArgumentException")
    void 割引率が上限超() {
        assertThatThrownBy(() -> new CorporateContract(
                "S-004", ShipperType.CORPORATE, new BigDecimal("0.31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountRate");
    }

    @Test
    @DisplayName("US22: 割引率が負数は IllegalArgumentException")
    void 割引率が負数() {
        assertThatThrownBy(() -> new CorporateContract(
                "S-005", ShipperType.CORPORATE, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountRate");
    }

    @Test
    @DisplayName("US22: 割引率 null は IllegalArgumentException")
    void 割引率がnull() {
        assertThatThrownBy(() -> new CorporateContract(
                "S-006", ShipperType.CORPORATE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountRate");
    }

    @Test
    @DisplayName("US22: INDIVIDUAL で割引率 0 以外は IllegalArgumentException")
    void INDIVIDUALで割引率がゼロ以外() {
        assertThatThrownBy(() -> new CorporateContract(
                "S-007", ShipperType.INDIVIDUAL, new BigDecimal("0.10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INDIVIDUAL");
    }

    @Test
    @DisplayName("US22: shipperId が空ならば IllegalArgumentException")
    void shipperIdが空() {
        assertThatThrownBy(() -> new CorporateContract(
                " ", ShipperType.CORPORATE, new BigDecimal("0.15")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shipperId");
    }

    @Test
    @DisplayName("US22: shipperType が null ならば IllegalArgumentException")
    void shipperTypeがnull() {
        assertThatThrownBy(() -> new CorporateContract(
                "S-008", null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shipperType");
    }
}
