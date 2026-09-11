package com.example.cargotracker.billing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 法人割引（US22）。
 *
 * <p><b>判定は列挙が答える。</b> 「法人なら割り引く」を呼ぶ側の if で書くと、
 * 呼ぶ場所が増えたぶんだけ抜ける。</p>
 */
class DiscountPolicyTest {

    private final DiscountPolicy policy = new DiscountPolicy();
    private final Money base = Money.yen(new BigDecimal("1190000"));

    @Test
    @DisplayName("US22 §2: 法人には契約割引率が当たる")
    void appliesTheContractRateToCorporateShippers() {
        Money discount = policy.discountFor(ShipperType.CORPORATE,
                DiscountRate.of(new BigDecimal("0.1500")), base);

        assertThat(discount.roundToUnit().amount()).isEqualByComparingTo("178500");
    }

    @Test
    @DisplayName("US22 §3: 個人には割引が当たらない（契約率が入っていても）")
    void neverDiscountsIndividuals() {
        // **個人に割引率が入っていることはありうる**（登録の誤り・法人からの変更）。
        // 種別で断ち切らないと、入っていた値がそのまま当たる。
        Money discount = policy.discountFor(ShipperType.INDIVIDUAL,
                DiscountRate.of(new BigDecimal("0.1500")), base);

        assertThat(discount.isZero()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ShipperType.class)
    @DisplayName("荷主種別を全部通す（扱っていない値が残らない）")
    void handlesEveryShipperType(ShipperType type) {
        // **列挙に値を足したら全箇所を回る。** 扱っていない場所は名乗り出ない。
        Money discount = policy.discountFor(type, DiscountRate.of(new BigDecimal("0.1000")), base);

        assertThat(discount.amount()).isNotNull();
        assertThat(type.discountable()).isEqualTo(!discount.isZero());
    }

    @Test
    @DisplayName("割引率が無い法人は 0 円（契約が無い法人もいる）")
    void corporateWithoutContractGetsNothing() {
        assertThat(policy.discountFor(ShipperType.CORPORATE, DiscountRate.none(), base).isZero())
                .isTrue();
    }
}
