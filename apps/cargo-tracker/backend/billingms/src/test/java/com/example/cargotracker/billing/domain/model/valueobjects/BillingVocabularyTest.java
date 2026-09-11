package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 請求の言葉（列挙と根拠）。
 *
 * <p><b>列挙名を画面に出さない。</b> 読む人は業務の言葉で読む。呼び名を持つことを
 * 値ごとに確かめる——1 つだけ確かめると、足した値に呼び名が無くても緑になる。</p>
 */
class BillingVocabularyTest {

    @ParameterizedTest
    @EnumSource(BillingStatus.class)
    @DisplayName("請求状態は列挙名でない呼び名を持つ")
    void billingStatusHasJapaneseLabel(BillingStatus status) {
        assertThat(status.label()).isNotBlank().isNotEqualTo(status.name());
    }

    @ParameterizedTest
    @EnumSource(LineItemType.class)
    @DisplayName("明細の種別は列挙名でない呼び名を持つ")
    void lineItemTypeHasJapaneseLabel(LineItemType type) {
        assertThat(type.label()).isNotBlank().isNotEqualTo(type.name());
    }

    @ParameterizedTest
    @EnumSource(PortRegion.class)
    @DisplayName("地域区分は列挙名でない呼び名を持つ")
    void portRegionHasJapaneseLabel(PortRegion region) {
        assertThat(region.label()).isNotBlank().isNotEqualTo(region.name());
    }

    @ParameterizedTest
    @EnumSource(ShipperType.class)
    @DisplayName("荷主種別は列挙名でない呼び名を持ち、名前から組み直せる")
    void shipperTypeHasJapaneseLabelAndRoundTrips(ShipperType type) {
        assertThat(type.label()).isNotBlank().isNotEqualTo(type.name());
        assertThat(ShipperType.of(type.name())).isEqualTo(type);
    }

    @Test
    @DisplayName("知らない荷主種別は断る（黙って個人にしない）")
    void refusesUnknownShipperType() {
        assertThatThrownBy(() -> ShipperType.of("COMPANY"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> ShipperType.of(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("調整を受け付けるのは算出済だけ（US21 §6）")
    void onlyCalculatedAcceptsAdjustment() {
        assertThat(BillingStatus.CALCULATED.acceptsAdjustment()).isTrue();
        for (BillingStatus status : BillingStatus.values()) {
            if (status != BillingStatus.CALCULATED) {
                assertThat(status.acceptsAdjustment())
                        .as("%s は調整を受け付けない", status.label())
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("地域区分の「重いほう」は宣言順で決まる")
    void heavierRegionWins() {
        assertThat(PortRegion.DOMESTIC.heavier(PortRegion.OCEAN)).isEqualTo(PortRegion.OCEAN);
        assertThat(PortRegion.OCEAN.heavier(PortRegion.DOMESTIC)).isEqualTo(PortRegion.OCEAN);
        assertThat(PortRegion.NEAR_SEA.heavier(PortRegion.NEAR_SEA)).isEqualTo(PortRegion.NEAR_SEA);
    }

    @Test
    @DisplayName("根拠の無い基本料金は作れない（金額だけでは根拠にならない）")
    void freightChargeNeedsItsBasis() {
        assertThatThrownBy(() -> new FreightCharge(null, List.of(PortRegion.OCEAN), "説明"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new FreightCharge(Money.yen(BigDecimal.ONE), List.of(), "説明"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new FreightCharge(Money.yen(BigDecimal.ONE), null, "説明"))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("金額の材料が欠けていれば作れない")
    void moneyNeedsAmountAndCurrency() {
        assertThatThrownBy(() -> new Money(null, "JPY"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, " "))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> Money.yen(BigDecimal.ONE).add(null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> Money.yen(BigDecimal.ONE).multiply(null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(Money.yen(BigDecimal.ONE).toString()).contains("JPY");
    }

    @Test
    @DisplayName("割引率の材料が欠けていれば作れない")
    void discountRateNeedsAValue() {
        assertThatThrownBy(() -> DiscountRate.of(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
