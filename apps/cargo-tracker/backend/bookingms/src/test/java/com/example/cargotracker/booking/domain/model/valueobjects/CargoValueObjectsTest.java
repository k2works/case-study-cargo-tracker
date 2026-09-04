package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 貨物予約の値オブジェクトが守る条件（domain-model.md「Cargo 集約の不変条件」）。 */
class CargoValueObjectsTest {

    @Test
    @DisplayName("重量は 0 より大きい")
    void weightIsPositive() {
        assertThat(Weight.ofKilograms("1200").kilograms()).isEqualByComparingTo("1200");
        assertThatThrownBy(() -> new Weight(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weight(BigDecimal.ZERO))
                .as("0 kg の貨物は料金計算の入力にならない")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Weight(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("寸法は 3 辺とも 0 より大きい")
    void dimensionsArePositive() {
        assertThat(Dimensions.of("120", "80", "100").lengthCm()).isEqualByComparingTo("120");
        assertThatThrownBy(() -> new Dimensions(null, BigDecimal.ONE, BigDecimal.ONE))
                .hasMessageContaining("長さ");
        assertThatThrownBy(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE))
                .hasMessageContaining("幅");
        assertThatThrownBy(() -> new Dimensions(BigDecimal.ONE, BigDecimal.ONE, null))
                .hasMessageContaining("高さ");
    }

    @Test
    @DisplayName("危険物申告は IMO クラスと UN 番号の両方が要る")
    void hazardousDeclarationNeedsBoth() {
        assertThatCode(() -> new HazardousDeclaration("3", "UN1263")).doesNotThrowAnyException();
        assertThatThrownBy(() -> new HazardousDeclaration(null, "UN1263"))
                .hasMessageContaining("IMO クラス");
        assertThatThrownBy(() -> new HazardousDeclaration(" ", "UN1263"))
                .hasMessageContaining("IMO クラス");
        assertThatThrownBy(() -> new HazardousDeclaration("3", null))
                .hasMessageContaining("UN 番号");
        assertThatThrownBy(() -> new HazardousDeclaration("3", " "))
                .hasMessageContaining("UN 番号");
    }

    @Test
    @DisplayName("温度条件は下限が上限を超えない")
    void temperatureRangeIsOrdered() {
        assertThatCode(() -> TemperatureRequirement.of("-20", "-10")).doesNotThrowAnyException();
        assertThatCode(() -> TemperatureRequirement.of("-10", "-10"))
                .as("同じ値は幅 0 の条件として認める")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TemperatureRequirement.of("-10", "-20"))
                .hasMessageContaining("下限が上限を超えて");
        assertThatThrownBy(() -> new TemperatureRequirement(null, BigDecimal.ONE))
                .hasMessageContaining("下限と上限");
        assertThatThrownBy(() -> new TemperatureRequirement(BigDecimal.ONE, null))
                .hasMessageContaining("下限と上限");
    }

    @Test
    @DisplayName("貨物仕様は必須項目を欠かさない")
    void cargoSpecificationRequiresFields() {
        Weight weight = Weight.ofKilograms("100");
        Dimensions dimensions = Dimensions.of("10", "10", "10");

        assertThatThrownBy(() -> new CargoSpecification(null, weight, dimensions, 1, "部品", null, null))
                .hasMessageContaining("貨物種別");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL, null, dimensions, 1, "部品", null, null))
                .hasMessageContaining("重量");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL, weight, null, 1, "部品", null, null))
                .hasMessageContaining("寸法");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL, weight, dimensions, 0, "部品", null, null))
                .hasMessageContaining("数量");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL, weight, dimensions, 1, " ", null, null))
                .hasMessageContaining("品名");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL, weight, dimensions, 1, null, null, null))
                .hasMessageContaining("品名");
    }

    @Test
    @DisplayName("経路仕様は端点と期限を欠かさない")
    void routeSpecificationRequiresEndpoints() {
        LocalDate deadline = LocalDate.of(2026, Month.DECEMBER, 1);
        assertThatThrownBy(() -> new RouteSpecification(null, Location.of("USNYC"), deadline))
                .hasMessageContaining("出発地と目的地は必須");
        assertThatThrownBy(() -> new RouteSpecification(Location.of("JPTYO"), null, deadline))
                .hasMessageContaining("出発地と目的地は必須");
        assertThatThrownBy(() -> new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), null))
                .hasMessageContaining("到着期限");
    }

    @Test
    @DisplayName("識別子は空を認めない")
    void identifiersRejectBlank() {
        assertThat(new BookingId("B-1").value()).isEqualTo("B-1");
        assertThat(new ShipperId("S-1").value()).isEqualTo("S-1");
        assertThatThrownBy(() -> new BookingId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookingId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShipperId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShipperId(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 遷移表は値の一覧から回す。名簿を書き写すと、値を足したときに増えた側だけが
     * 検査されない（IT1 レビュー H3 と同じ形）。
     */
    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("すべての状態が遷移表を持つ")
    void everyStatusHasTransitions(BookingStatus status) {
        assertThatCode(() -> status.canTransitionTo(BookingStatus.CANCELLED))
                .doesNotThrowAnyException();
        assertThatCode(status::cancellableImmediately).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("終わった状態からは動かない")
    void terminalStatusesHaveNoNext() {
        for (BookingStatus next : BookingStatus.values()) {
            assertThat(BookingStatus.SETTLED.canTransitionTo(next)).isFalse();
            assertThat(BookingStatus.CANCELLED.canTransitionTo(next)).isFalse();
        }
        assertThat(BookingStatus.SETTLED.cancellableImmediately()).isFalse();
        assertThat(BookingStatus.CANCELLED.cancellableImmediately()).isFalse();
    }
}
