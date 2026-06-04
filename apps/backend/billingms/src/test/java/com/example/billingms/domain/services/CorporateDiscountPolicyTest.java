package com.example.billingms.domain.services;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CorporateDiscountPolicy} の検証（US22 / IT7 タスク 3.1）。
 *
 * <p>S23 UI サンプル: 山田商事（CORPORATE、15%）に基本料金 330,000 円を適用 →
 * 割引額 49,500 円、割引後 280,500 円。</p>
 */
class CorporateDiscountPolicyTest {

    private final CorporateDiscountPolicy policy = new CorporateDiscountPolicy();

    @Test
    @DisplayName("US22: S23 UI 山田商事（CORPORATE 15%）：basicFee 330,000 → 割引額 49,500")
    void S23サンプルの割引額() {
        CorporateContract contract = new CorporateContract(
                "S-001", ShipperType.CORPORATE, new BigDecimal("0.15"));

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("330000"), contract);

        assertThat(discount).isEqualByComparingTo("49500");
    }

    @Test
    @DisplayName("US22: INDIVIDUAL 荷主の割引額は 0")
    void INDIVIDUAL荷主の割引額() {
        CorporateContract contract = new CorporateContract(
                "S-002", ShipperType.INDIVIDUAL, BigDecimal.ZERO);

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("330000"), contract);

        assertThat(discount).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("US22: 割引率 0.30（上限）：basicFee 1,000,000 → 割引額 300,000")
    void 上限割引率の割引額() {
        CorporateContract contract = new CorporateContract(
                "S-003", ShipperType.CORPORATE, new BigDecimal("0.30"));

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("1000000"), contract);

        assertThat(discount).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("US22: 割引率 0.00（CORPORATE だが割引契約なし）：割引額 0")
    void 契約なしCORPORATEの割引額() {
        CorporateContract contract = new CorporateContract(
                "S-004", ShipperType.CORPORATE, BigDecimal.ZERO);

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("330000"), contract);

        assertThat(discount).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("US22: 結果は HALF_UP 丸めで円単位整数（小数第 0 位）")
    void 円単位の丸め() {
        // 12,345 × 0.07 = 864.15 → 864（HALF_UP）
        CorporateContract contract = new CorporateContract(
                "S-005", ShipperType.CORPORATE, new BigDecimal("0.07"));

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("12345"), contract);

        assertThat(discount).isEqualByComparingTo("864");
    }

    @Test
    @DisplayName("US22: 0.5 円単位の HALF_UP は切り上げ")
    void 五捨五入の境界() {
        // 1000 × 0.0125 = 12.50 → 13（HALF_UP）
        CorporateContract contract = new CorporateContract(
                "S-006", ShipperType.CORPORATE, new BigDecimal("0.0125"));

        BigDecimal discount = policy.calculateDiscount(new BigDecimal("1000"), contract);

        assertThat(discount).isEqualByComparingTo("13");
    }

    @Test
    @DisplayName("US22: basicFee が null は IllegalArgumentException")
    void basicFeeがnull() {
        CorporateContract contract = new CorporateContract(
                "S-007", ShipperType.CORPORATE, new BigDecimal("0.15"));

        assertThatThrownBy(() -> policy.calculateDiscount(null, contract))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basicFee");
    }

    @Test
    @DisplayName("US22: basicFee が負数は IllegalArgumentException")
    void basicFeeが負数() {
        CorporateContract contract = new CorporateContract(
                "S-008", ShipperType.CORPORATE, new BigDecimal("0.15"));

        assertThatThrownBy(() -> policy.calculateDiscount(new BigDecimal("-1"), contract))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basicFee");
    }

    @Test
    @DisplayName("US22: contract が null は IllegalArgumentException")
    void contractがnull() {
        assertThatThrownBy(() -> policy.calculateDiscount(new BigDecimal("330000"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract");
    }
}
