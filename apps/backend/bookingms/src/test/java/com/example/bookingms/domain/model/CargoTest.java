package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("貨物予約")
class CargoTest {

    private static final RouteSpecification ROUTE = RouteSpecification.restore(
            Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
            LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 20));

    private static final HazardousDeclaration DECLARATION =
            HazardousDeclaration.of("Class 3", "UN1263", "PAINT");

    private static final TemperatureRequirement TEMPERATURE =
            TemperatureRequirement.of(new BigDecimal("-20"), new BigDecimal("-15"));

    private static CargoSpecification specification(CargoType type,
            HazardousDeclaration declaration, TemperatureRequirement temperature) {
        return new CargoSpecification(type, new BigDecimal("12000"), 20, "電子部品",
                Dimensions.of(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100")),
                declaration, temperature);
    }

    @Nested
    @DisplayName("受け付けたとき")
    class WhenBooked {

        @Test
        @DisplayName("仮受付になり、まだ動いていない状態を持つ")
        void startsAsPreliminary() {
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
            // 「まだ動いていない」は空欄ではなく意味のある状態（ADR-009）
            assertThat(cargo.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
            assertThat(cargo.routingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
        }

        @Test
        @DisplayName("予約番号はまだ持たない（採番は永続化の経路で行う）")
        void hasNoBookingIdYet() {
            // 集約側で組み立てるとシーケンスと衝突した番号を発行できてしまう（ADR-011）
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.bookingId()).isEmpty();
        }

        @Test
        @DisplayName("貨物仕様と輸送条件を保持する")
        void holdsSpecifications() {
            Cargo cargo = Cargo.book(1L, specification(CargoType.GENERAL, null, null), ROUTE);

            assertThat(cargo.shipperId()).isEqualTo(1L);
            assertThat(cargo.type()).isEqualTo(CargoType.GENERAL);
            assertThat(cargo.weightKg()).isEqualByComparingTo(new BigDecimal("12000"));
            assertThat(cargo.routeSpecification()).isEqualTo(ROUTE);
            assertThat(cargo.specification().quantity()).isEqualTo(20);
            assertThat(cargo.specification().description()).isEqualTo("電子部品");
        }

        @Test
        @DisplayName("荷主・輸送条件・種別が無い予約は受け付けない")
        void rejectsMissingEssentials() {
            assertThatThrownBy(() ->
                    Cargo.book(null, specification(CargoType.GENERAL, null, null), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("荷主");
            assertThatThrownBy(() ->
                    Cargo.book(1L, specification(CargoType.GENERAL, null, null), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("輸送条件");
            assertThatThrownBy(() -> Cargo.book(1L, specification(null, null, null), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("貨物種別");
            assertThatThrownBy(() -> Cargo.book(1L, null, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("重量が 0 以下の予約は受け付けない")
        void rejectsNonPositiveWeight() {
            CargoSpecification zero = CargoSpecification.general(BigDecimal.ZERO, null, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, zero, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("重量");
        }

        @Test
        @DisplayName("個数が 0 以下の予約は受け付けない")
        void rejectsNonPositiveQuantity() {
            CargoSpecification zero =
                    CargoSpecification.general(new BigDecimal("100"), 0, null, null);

            assertThatThrownBy(() -> Cargo.book(1L, zero, ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("個数");
        }

        @Test
        @DisplayName("個数・品名・寸法は任意")
        void allowsOptionalSpecification() {
            CargoSpecification minimal =
                    CargoSpecification.general(new BigDecimal("100"), null, null, null);

            assertThatCode(() -> Cargo.book(1L, minimal, ROUTE)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("危険物・冷凍の追加情報（US05）")
    class SpecialCargo {

        @Test
        @DisplayName("危険物は申告が必須")
        void hazardousRequiresDeclaration() {
            assertThatThrownBy(() ->
                    Cargo.book(1L, specification(CargoType.HAZARDOUS, null, null), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物申告");
        }

        @Test
        @DisplayName("冷凍・冷蔵は温度条件が必須")
        void refrigeratedRequiresTemperature() {
            assertThatThrownBy(() ->
                    Cargo.book(1L, specification(CargoType.REFRIGERATED, null, null), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("保管温度");
        }

        @Test
        @DisplayName("一般貨物に危険物申告や温度条件は付けられない")
        void generalCannotCarrySpecialInformation() {
            // 付け忘れと同じく、付けすぎも誤り。経路設計（IT3）が扱いを判断できなくなる
            assertThatThrownBy(() ->
                    Cargo.book(1L, specification(CargoType.GENERAL, DECLARATION, null), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物にだけ");
            assertThatThrownBy(() ->
                    Cargo.book(1L, specification(CargoType.GENERAL, null, TEMPERATURE), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冷凍・冷蔵貨物にだけ");
        }

        @Test
        @DisplayName("危険物に温度条件、冷凍に危険物申告は付けられない")
        void cannotMixSpecialInformation() {
            assertThatThrownBy(() -> Cargo.book(
                    1L, specification(CargoType.HAZARDOUS, DECLARATION, TEMPERATURE), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cargo.book(
                    1L, specification(CargoType.REFRIGERATED, DECLARATION, TEMPERATURE), ROUTE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("正しく揃っていれば受け付け、経路設計が読める形で保持する")
        void acceptsWellFormedSpecialCargo() {
            Cargo hazardous =
                    Cargo.book(1L, specification(CargoType.HAZARDOUS, DECLARATION, null), ROUTE);
            Cargo refrigerated =
                    Cargo.book(1L, specification(CargoType.REFRIGERATED, null, TEMPERATURE), ROUTE);

            assertThat(hazardous.requiresHazardousDeclaration()).isTrue();
            assertThat(hazardous.hazardousDeclaration()).contains(DECLARATION);
            assertThat(hazardous.requiresTemperatureRequirement()).isFalse();
            assertThat(hazardous.temperatureRequirement()).isEmpty();

            assertThat(refrigerated.requiresTemperatureRequirement()).isTrue();
            assertThat(refrigerated.temperatureRequirement()).contains(TEMPERATURE);
            assertThat(refrigerated.requiresHazardousDeclaration()).isFalse();
            assertThat(refrigerated.hazardousDeclaration()).isEmpty();
        }
    }

    @Test
    @DisplayName("復元では検査しない（規則が無かったころの行が読めなくなる）")
    void restoreDoesNotValidate() {
        Cargo restored = Cargo.restore(1L, BookingId.of("BKG-2026000001"), 1L,
                BookingStatus.PRELIMINARY, TransportStatus.NOT_RECEIVED, RoutingStatus.NOT_ROUTED,
                // 危険物なのに申告が無い（列が無かったころの行）
                specification(CargoType.HAZARDOUS, null, null), ROUTE);

        assertThat(restored.bookingId()).contains(BookingId.of("BKG-2026000001"));
        assertThat(restored.hazardousDeclaration()).isEmpty();
        assertThat(restored.id()).isEqualTo(1L);
    }
}
