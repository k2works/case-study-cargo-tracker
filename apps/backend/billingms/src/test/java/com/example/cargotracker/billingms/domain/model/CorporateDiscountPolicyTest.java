package com.example.cargotracker.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billingms.domain.model.services.CorporateDiscountPolicy;
import com.example.cargotracker.billingms.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.billingms.domain.model.valueobjects.Money;
import com.example.cargotracker.billingms.domain.model.valueobjects.ShipperType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CorporateDiscountPolicy ドメインサービスのユニットテスト（US22）。
 */
@DisplayName("CorporateDiscountPolicy ドメインサービステスト")
class CorporateDiscountPolicyTest {

    private final CorporateDiscountPolicy policy = new CorporateDiscountPolicy();

    @Test
    @DisplayName("法人荷主に割引率 20% を適用すると割引後金額が返る")
    void apply_法人20割引() {
        var contract = new CorporateContract("SHP-001", ShipperType.CORPORATE, new BigDecimal("0.20"));
        var basic = Money.of(new BigDecimal("100000"), "JPY");

        var result = policy.apply(basic, contract);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(result.currency().getCurrencyCode()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("個人荷主は割引率 0%（割引なし）")
    void apply_個人割引なし() {
        var contract = new CorporateContract("SHP-002", ShipperType.INDIVIDUAL, BigDecimal.ZERO);
        var basic = Money.of(new BigDecimal("50000"), "JPY");

        var result = policy.apply(basic, contract);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("割引率 0 の法人荷主は割引なし")
    void apply_法人割引率ゼロ() {
        var contract = new CorporateContract("SHP-003", ShipperType.CORPORATE, BigDecimal.ZERO);
        var basic = Money.of(new BigDecimal("30000"), "JPY");

        var result = policy.apply(basic, contract);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("割引率が 0.30 を超えると IllegalArgumentException")
    void apply_割引率上限超過() {
        assertThatThrownBy(() ->
                new CorporateContract("SHP-004", ShipperType.CORPORATE, new BigDecimal("0.31")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("割引率は 30%");
    }

    @Test
    @DisplayName("getDiscountRate は法人契約の割引率を返す")
    void getDiscountRate_法人() {
        var contract = new CorporateContract("SHP-005", ShipperType.CORPORATE, new BigDecimal("0.15"));
        assertThat(policy.getDiscountRate(contract)).isEqualByComparingTo(new BigDecimal("0.15"));
    }

    @Test
    @DisplayName("getDiscountRate は個人荷主に 0 を返す")
    void getDiscountRate_個人() {
        var contract = new CorporateContract("SHP-006", ShipperType.INDIVIDUAL, BigDecimal.ZERO);
        assertThat(policy.getDiscountRate(contract)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
