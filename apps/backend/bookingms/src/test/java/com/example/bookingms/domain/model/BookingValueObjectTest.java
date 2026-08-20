package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("予約の値オブジェクト")
class BookingValueObjectTest {

    @Nested
    @DisplayName("予約番号")
    class BookingIdSpec {

        @Test
        @DisplayName("BKG- + 西暦 4 桁 + 連番 6 桁を受け付ける")
        void acceptsWellFormed() {
            assertThat(BookingId.of("BKG-2026000001").value()).isEqualTo("BKG-2026000001");
        }

        @ParameterizedTest
        @ValueSource(strings = {"BKG-202600001", "BKG-20260000001", "BK-2026000001",
            "bkg-2026000001", "2026000001", ""})
        @DisplayName("形式の違う番号は受け付けない")
        void rejectsMalformed(String value) {
            // 5 サービスが参照するキーであり、値そのものが契約になる
            assertThatThrownBy(() -> BookingId.of(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("予約番号");
        }

        @Test
        @DisplayName("null は受け付けない")
        void rejectsNull() {
            assertThatThrownBy(() -> BookingId.of(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("同じ番号は等しい")
        void equality() {
            assertThat(BookingId.of("BKG-2026000001"))
                    .isEqualTo(BookingId.of("BKG-2026000001"))
                    .hasSameHashCodeAs(BookingId.of("BKG-2026000001"))
                    .isNotEqualTo(BookingId.of("BKG-2026000002"))
                    .isNotEqualTo("BKG-2026000001")
                    .hasToString("BKG-2026000001");
        }
    }

    @Nested
    @DisplayName("外寸")
    class DimensionsSpec {

        @Test
        @DisplayName("3 辺を保持する")
        void holdsValues() {
            Dimensions dimensions = Dimensions.of(
                    new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"));

            assertThat(dimensions.lengthCm()).isEqualByComparingTo("120");
            assertThat(dimensions.widthCm()).isEqualByComparingTo("80");
            assertThat(dimensions.heightCm()).isEqualByComparingTo("100");
            assertThat(dimensions).hasToString("120 × 80 × 100 cm");
        }

        @Test
        @DisplayName("0 以下の辺は受け付けない")
        void rejectsNonPositive() {
            BigDecimal negative = new BigDecimal("-1");

            assertThatThrownBy(() -> Dimensions.of(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("長さ");
            assertThatThrownBy(() -> Dimensions.of(BigDecimal.ONE, negative, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("幅");
            assertThatThrownBy(() -> Dimensions.of(BigDecimal.ONE, BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("高さ");
        }

        @Test
        @DisplayName("同じ寸法は等しい（末尾のゼロは無視する）")
        void equality() {
            Dimensions one = Dimensions.of(
                    new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"));
            Dimensions other = Dimensions.of(
                    new BigDecimal("120.00"), new BigDecimal("80.0"), new BigDecimal("100.000"));

            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
            assertThat(one).isNotEqualTo("120x80x100");
        }
    }

    @Nested
    @DisplayName("危険物申告")
    class HazardousDeclarationSpec {

        @Test
        @DisplayName("3 項目を保持し、前後の空白は落とす")
        void holdsValues() {
            HazardousDeclaration declaration =
                    HazardousDeclaration.of(" Class 3 ", " UN1263 ", " PAINT ");

            assertThat(declaration.hazardousClass()).isEqualTo("Class 3");
            assertThat(declaration.unNumber()).isEqualTo("UN1263");
            assertThat(declaration.properShippingName()).isEqualTo("PAINT");
            assertThat(declaration).hasToString("Class 3 / UN1263 / PAINT");
        }

        @Test
        @DisplayName("欠けた申告は受け付けない（3 項目そろって初めて法的要件を満たす）")
        void rejectsIncomplete() {
            assertThatThrownBy(() -> HazardousDeclaration.of("", "UN1263", "PAINT"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("危険物クラス");
            assertThatThrownBy(() -> HazardousDeclaration.of("Class 3", "UN1263", " "))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("正式品名");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1263", "UN126", "UN12630", "un1263", "UN-1263"})
        @DisplayName("UN 番号の形式が違えば受け付けない")
        void rejectsMalformedUnNumber(String unNumber) {
            assertThatThrownBy(() -> HazardousDeclaration.of("Class 3", unNumber, "PAINT"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UN 番号");
        }

        @Test
        @DisplayName("null は受け付けない")
        void rejectsNull() {
            assertThatThrownBy(() -> HazardousDeclaration.of(null, "UN1263", "PAINT"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HazardousDeclaration.of("Class 3", null, "PAINT"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HazardousDeclaration.of("Class 3", "UN1263", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("復元では検査しない")
        void restoreDoesNotValidate() {
            assertThat(HazardousDeclaration.restore(null, null, null).unNumber()).isNull();
        }

        @Test
        @DisplayName("同じ申告は等しい")
        void equality() {
            HazardousDeclaration one = HazardousDeclaration.of("Class 3", "UN1263", "PAINT");

            HazardousDeclaration same = HazardousDeclaration.of("Class 3", "UN1263", "PAINT");
            HazardousDeclaration other = HazardousDeclaration.of("Class 8", "UN1263", "PAINT");

            assertThat(one)
                    .isEqualTo(same)
                    .hasSameHashCodeAs(same)
                    .isNotEqualTo(other)
                    .isNotEqualTo("Class 3");
        }
    }

    @Nested
    @DisplayName("温度条件")
    class TemperatureRequirementSpec {

        @Test
        @DisplayName("下限と上限を保持する")
        void holdsValues() {
            TemperatureRequirement requirement =
                    TemperatureRequirement.of(new BigDecimal("-20"), new BigDecimal("-15"));

            assertThat(requirement.minCelsius()).isEqualByComparingTo("-20");
            assertThat(requirement.maxCelsius()).isEqualByComparingTo("-15");
            assertThat(requirement).hasToString("-20〜-15℃");
        }

        @Test
        @DisplayName("下限と上限が同じ条件は受け付ける")
        void acceptsEqualBounds() {
            assertThat(TemperatureRequirement.of(new BigDecimal("4"), new BigDecimal("4")))
                    .isNotNull();
        }

        @Test
        @DisplayName("下限が上限を超える条件は受け付けない")
        void rejectsInvertedBounds() {
            // 満たせる温度が存在せず、荷役で必ず破られる
            BigDecimal min = new BigDecimal("-10");
            BigDecimal max = new BigDecimal("-20");

            assertThatThrownBy(() -> TemperatureRequirement.of(min, max))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("下限が上限を超えています");
        }

        @Test
        @DisplayName("片方だけの条件は受け付けない")
        void rejectsPartialBounds() {
            BigDecimal min = new BigDecimal("-20");
            BigDecimal max = new BigDecimal("-15");

            assertThatThrownBy(() -> TemperatureRequirement.of(min, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TemperatureRequirement.of(null, max))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("復元では検査しない")
        void restoreDoesNotValidate() {
            TemperatureRequirement restored =
                    TemperatureRequirement.restore(new BigDecimal("-10"), new BigDecimal("-20"));

            assertThat(restored.minCelsius()).isEqualByComparingTo("-10");
        }

        @Test
        @DisplayName("同じ条件は等しい")
        void equality() {
            TemperatureRequirement one =
                    TemperatureRequirement.of(new BigDecimal("-20"), new BigDecimal("-15"));

            assertThat(one)
                    .isEqualTo(TemperatureRequirement.of(new BigDecimal("-20.0"), new BigDecimal("-15.00")))
                    .hasSameHashCodeAs(TemperatureRequirement.of(new BigDecimal("-20.0"), new BigDecimal("-15.00")))
                    .isNotEqualTo(TemperatureRequirement.of(new BigDecimal("-25"), new BigDecimal("-15")))
                    .isNotEqualTo("-20〜-15");
        }
    }
}
