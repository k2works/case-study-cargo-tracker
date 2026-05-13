package com.example.cargotracker.bookingms.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("US04 値オブジェクト群")
class ValueObjectsTest {

    @Nested
    @DisplayName("BookingId")
    class BookingIdTest {
        @Test
        @DisplayName("空文字列は拒否される")
        void 空文字列拒否() {
            assertThatThrownBy(() -> new BookingId("")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("generate は UUID 形式を返す")
        void generateはUUID形式() {
            assertThat(BookingId.generate().value()).hasSize(36);
        }
    }

    @Nested
    @DisplayName("TrackingNumber")
    class TrackingNumberTest {
        @Test
        @DisplayName("TRK- + 大文字英数 10 桁は受け入れる")
        void 正常な書式は受け入れる() {
            assertThat(new TrackingNumber("TRK-AB12CD3456").value()).isEqualTo("TRK-AB12CD3456");
        }

        @Test
        @DisplayName("プレフィックス TRK- が無いと拒否")
        void プレフィックスなしは拒否() {
            assertThatThrownBy(() -> new TrackingNumber("AB12CD3456"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("小文字を含むと拒否")
        void 小文字は拒否() {
            assertThatThrownBy(() -> new TrackingNumber("TRK-ab12CD3456"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("UnLocode")
    class UnLocodeTest {
        @Test
        @DisplayName("有効な UN/LOCODE は受け入れる")
        void 有効な書式() {
            assertThat(new UnLocode("JPYOK").value()).isEqualTo("JPYOK");
            assertThat(new UnLocode("USLAX").value()).isEqualTo("USLAX");
        }

        @Test
        @DisplayName("4 文字は拒否")
        void 短すぎる() {
            assertThatThrownBy(() -> new UnLocode("JPYO")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("小文字を含むと拒否")
        void 小文字を含む() {
            assertThatThrownBy(() -> new UnLocode("jpyok")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Dimensions")
    class DimensionsTest {
        @Test
        @DisplayName("負値は拒否される")
        void 負値拒否() {
            assertThatThrownBy(() -> new Dimensions(-1, 10, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("0 は許容される（不明寸法）")
        void ゼロ許容() {
            var d = new Dimensions(0, 0, 0);
            assertThat(d.lengthCm()).isZero();
        }
    }

    @Nested
    @DisplayName("Money")
    class MoneyTest {
        @Test
        @DisplayName("正の金額と 3 文字通貨は受け入れる")
        void 正常() {
            var m = new Money(new BigDecimal("1000.00"), "JPY");
            assertThat(m.amount()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("負の金額は拒否")
        void 負の金額() {
            assertThatThrownBy(() -> new Money(new BigDecimal("-1"), "JPY"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("通貨が 3 文字でないと拒否")
        void 通貨書式() {
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, "Japanese"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("TemperatureCondition")
    class TemperatureConditionTest {
        @Test
        @DisplayName("min <= max は受け入れる")
        void 正常範囲() {
            var t = new TemperatureCondition(new BigDecimal("-20"), new BigDecimal("-10"));
            assertThat(t.maxCelsius()).isEqualByComparingTo("-10");
        }

        @Test
        @DisplayName("min > max は拒否")
        void 逆転拒否() {
            assertThatThrownBy(() -> new TemperatureCondition(new BigDecimal("10"), new BigDecimal("5")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CargoSpecification")
    class CargoSpecificationTest {
        private final Dimensions dim = new Dimensions(100, 100, 100);

        @Test
        @DisplayName("GENERAL は HazardInfo / TemperatureCondition なしで生成可能")
        void general生成() {
            var spec = CargoSpecification.general(new BigDecimal("100"), dim, 1, "産業機械");
            assertThat(spec.cargoType()).isEqualTo(CargoType.GENERAL);
        }

        @Test
        @DisplayName("HAZARDOUS で HazardInfo なしは拒否")
        void hazardousは申告必須() {
            assertThatThrownBy(() -> new CargoSpecification(
                    CargoType.HAZARDOUS, new BigDecimal("100"), dim, 1, "燃料",
                    null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("HazardInfo");
        }

        @Test
        @DisplayName("REFRIGERATED で TemperatureCondition なしは拒否")
        void refrigeratedは温度必須() {
            assertThatThrownBy(() -> new CargoSpecification(
                    CargoType.REFRIGERATED, new BigDecimal("100"), dim, 1, "冷凍食品",
                    null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TemperatureCondition");
        }

        @Test
        @DisplayName("GENERAL で HazardInfo を設定すると拒否")
        void general整合性() {
            var hazard = new HazardInfo("3", "1170", "引火性液体");
            assertThatThrownBy(() -> new CargoSpecification(
                    CargoType.GENERAL, new BigDecimal("100"), dim, 1, "産業機械",
                    hazard, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("weightKg が 0 以下は拒否")
        void weightKg境界() {
            assertThatThrownBy(() -> CargoSpecification.general(
                    BigDecimal.ZERO, dim, 1, "産業機械"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RouteSpecification")
    class RouteSpecificationTest {
        @Test
        @DisplayName("origin と destination が異なれば受け入れる")
        void 正常() {
            var spec = new RouteSpecification(
                    Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
            assertThat(spec.origin().unLocode().value()).isEqualTo("JPYOK");
        }

        @Test
        @DisplayName("origin と destination が同一は拒否")
        void 同一拒否() {
            assertThatThrownBy(() -> new RouteSpecification(
                    Location.of("JPYOK"), Location.of("JPYOK"), LocalDate.of(2026, 12, 31)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
