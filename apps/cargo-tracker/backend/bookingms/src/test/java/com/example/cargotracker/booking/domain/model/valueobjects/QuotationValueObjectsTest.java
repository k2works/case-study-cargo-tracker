package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 見積の値オブジェクトが置いた守り（US01）。
 *
 * <p><b>書いた保証は赤で固定する。</b> 「必須です」「長すぎます」は書いただけで
 * 守った気になりやすい——外しても、通常の経路は緑のままである。</p>
 */
class QuotationValueObjectsTest {

    @Nested
    @DisplayName("EstimatedAmount")
    class EstimatedAmountTest {

        @Test
        @DisplayName("金額と通貨は必須（欠けたまま通すと 0 円の見積が出る）")
        void refusesMissingParts() {
            assertThatThrownBy(() -> new EstimatedAmount(null, "JPY"))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> new EstimatedAmount(BigDecimal.ONE, null))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> new EstimatedAmount(BigDecimal.ONE, " "))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("負の概算は断る（割引の向きを取り違えると出る）")
        void refusesNegativeAmount() {
            assertThatThrownBy(() -> EstimatedAmount.yen(new BigDecimal("-1")))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("0 円かどうかを答える（候補が無い見積は 0 円）")
        void answersWhetherItIsZero() {
            assertThat(EstimatedAmount.zero().isZero()).isTrue();
            assertThat(EstimatedAmount.yen(new BigDecimal("1")).isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("QuotationId")
    class QuotationIdTest {

        @Test
        @DisplayName("空は断る")
        void refusesBlank() {
            assertThatThrownBy(() -> new QuotationId(null))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> new QuotationId(" "))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("列の長さを超えたら断る（超えると投影だけが静かに落ちる）")
        void refusesTooLong() {
            assertThatThrownBy(() -> new QuotationId("Q-".repeat(QuotationId.MAX_LENGTH)))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThat(new QuotationId("Q-" + "a".repeat(QuotationId.MAX_LENGTH - 2)).value())
                    .hasSize(QuotationId.MAX_LENGTH);
        }
    }

    @Nested
    @DisplayName("QuotedRoute")
    class QuotedRouteTest {

        private static Leg leg() {
            return new Leg("V-Q-001", Location.of("JPTYO"), Location.of("SGSIN"),
                    Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-10T18:00:00Z"));
        }

        @Test
        @DisplayName("区間が無い候補は作れない（航海番号も経由港も出せない）")
        void refusesEmptyLegs() {
            assertThatThrownBy(() -> new QuotedRoute(List.of(), 3,
                    EstimatedAmount.zero(), 0))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> new QuotedRoute(null, 3, EstimatedAmount.zero(), 0))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("概算の無い候補は作れない")
        void refusesMissingEstimate() {
            assertThatThrownBy(() -> new QuotedRoute(List.of(leg()), 3, null, 0))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("超過日数が負の候補は作れない（間に合う候補は 0）")
        void refusesNegativeOverdueDays() {
            assertThatThrownBy(() -> new QuotedRoute(List.of(leg()), 3,
                    EstimatedAmount.zero(), -1))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("経由港は出発地から順に並ぶ（候補ごとに違う）")
        void listsPortsInOrder() {
            assertThat(new QuotedRoute(List.of(leg()), 3, EstimatedAmount.zero(), 0).ports())
                    .containsExactly("JPTYO", "SGSIN");
        }
    }

    @Nested
    @DisplayName("QuotationRates")
    class QuotationRatesTest {

        private static QuotationRates rates(Map<PortRegion, BigDecimal> regionFactors,
                Map<String, BigDecimal> cargoTypeFactors,
                Map<String, PortRegion> countryRegions) {
            return new QuotationRates(new BigDecimal("50000"), regionFactors,
                    cargoTypeFactors, countryRegions);
        }

        private static final Map<PortRegion, BigDecimal> REGIONS = Map.of(
                PortRegion.DOMESTIC, new BigDecimal("1.0"),
                PortRegion.NEAR_SEA, new BigDecimal("2.5"),
                PortRegion.OCEAN, new BigDecimal("6.0"));

        @Test
        @DisplayName("料率が欠けていたら組み立ての時点で断る（請求のたびに落ちない）")
        void refusesIncompleteTables() {
            assertThatThrownBy(() -> new QuotationRates(null, REGIONS,
                    Map.of("GENERAL", BigDecimal.ONE), Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> rates(Map.of(), Map.of("GENERAL", BigDecimal.ONE),
                    Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> rates(REGIONS, Map.of(),
                    Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("基準運賃が 0 以下なら断る（設定の消し忘れで全部 0 円になる）")
        void refusesNonPositiveBaseFare() {
            assertThatThrownBy(() -> new QuotationRates(BigDecimal.ZERO, REGIONS,
                    Map.of("GENERAL", BigDecimal.ONE), Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("係数の表そのものが無ければ断る")
        void refusesNullTables() {
            assertThatThrownBy(() -> rates(null, Map.of("GENERAL", BigDecimal.ONE),
                    Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
            assertThatThrownBy(() -> rates(REGIONS, null, Map.of("JP", PortRegion.DOMESTIC)))
                    .isInstanceOf(BusinessRuleViolation.class);
        }

        @Test
        @DisplayName("国の表が無ければ全部遠洋として数える（安いほうに倒さない）")
        void treatsAMissingCountryTableAsOcean() {
            assertThat(rates(REGIONS, Map.of("GENERAL", BigDecimal.ONE), null)
                    .regionOf(Location.of("JPTYO").unLocode()))
                    .isEqualTo(PortRegion.OCEAN);
        }

        @Test
        @DisplayName("表に無い国は遠洋（安いほうに倒さない）")
        void treatsUnknownCountryAsOcean() {
            assertThat(rates(REGIONS, Map.of("GENERAL", BigDecimal.ONE),
                    Map.of("JP", PortRegion.DOMESTIC)).regionOf(Location.of("USNYC").unLocode()))
                    .isEqualTo(PortRegion.OCEAN);
        }
    }
}
