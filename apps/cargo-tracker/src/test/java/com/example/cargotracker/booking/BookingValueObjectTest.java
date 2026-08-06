package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Booking Context の値オブジェクトの不変条件を検証する。
 *
 * <p>不変条件は統合テストではなくユニットテストで固定する（IT1 ふりかえり Try T2）。
 * 統合テストで確かめると、失敗したときに「業務ルールが違う」のか
 * 「配線が違う」のかが切り分けられない。
 *
 * <p>境界値を必ず含める。**「不正な値を弾く」テストだけを書くと、
 * 境界のちょうど内側を誤って弾いていても緑になる。**
 */
class BookingValueObjectTest {

    private static final Location 大阪 = Location.of("JPOSA");
    private static final Location ロサンゼルス = Location.of("USLAX");

    @Nested
    @DisplayName("Location（共有カーネル）")
    class LocationTest {

        @Test
        void 地点はUNLOCODE形式の5文字を受け入れる() {
            assertThat(Location.of("JPOSA").unlocode()).isEqualTo("JPOSA");
        }

        @ParameterizedTest
        @ValueSource(strings = {"JPOS", "JPOSAX", "jposa", "JP-SA", "12345", ""})
        void 地点はUNLOCODE形式でない値を拒否する(String invalid) {
            assertThatThrownBy(() -> Location.of(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 未指定を拒否する() {
            assertThatThrownBy(() -> Location.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("BookingId")
    class BookingIdTest {

        @Test
        void 生成するたびに異なる値になる() {
            assertThat(BookingId.generate()).isNotEqualTo(BookingId.generate());
        }

        @Test
        void 同じUUIDなら等しい() {
            BookingId id = BookingId.generate();
            assertThat(BookingId.of(id.value().toString())).isEqualTo(id);
        }

        @Test
        void 未指定を拒否する() {
            assertThatThrownBy(() -> new BookingId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RouteSpecification")
    class RouteSpecificationTest {

        private static final LocalDate 今日 = LocalDate.of(2026, 8, 6);

        @Test
        void 出発地と目的地が異なれば生成できる() {
            RouteSpecification spec =
                    RouteSpecification.of(大阪, ロサンゼルス, 今日.plusDays(30), 今日);
            assertThat(spec.origin()).isEqualTo(大阪);
            assertThat(spec.destination()).isEqualTo(ロサンゼルス);
        }

        /** ビジネスルール 2（domain-model.md）。DB の CHECK 制約とドメインの両方で守る。 */
        @Test
        void 出発地と目的地が同じ予約を拒否する() {
            assertThatThrownBy(() -> RouteSpecification.of(大阪, 大阪, 今日.plusDays(30), 今日))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("出発地と目的地");
        }

        @Test
        void 到着期限が過去の予約を拒否する() {
            assertThatThrownBy(() -> RouteSpecification.of(大阪, ロサンゼルス, 今日.minusDays(1), 今日))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("到着期限");
        }

        /**
         * 境界値。**当日は過去ではない。**
         *
         * <p>「期限切れを弾く」だけを見て {@code isBefore} を {@code isAfter} の否定で
         * 書くと、当日の予約まで弾いてしまう。当日受付は業務上ありふれている。
         */
        @Test
        void 到着期限が当日の予約は受け付ける() {
            assertThatCode(() -> RouteSpecification.of(大阪, ロサンゼルス, 今日, 今日))
                    .doesNotThrowAnyException();
        }

        @Test
        void 到着期限がnullの予約を拒否する() {
            assertThatThrownBy(() -> RouteSpecification.of(大阪, ロサンゼルス, null, 今日))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Weight")
    class WeightTest {

        @Test
        void 正の重量を受け入れる() {
            assertThat(Weight.ofKilograms(new BigDecimal("1200.500")).kilograms())
                    .isEqualByComparingTo("1200.500");
        }

        /** 境界値。0 は「重さのない貨物」であり業務上存在しない（DB の CHECK も weight > 0）。 */
        @Test
        void ゼロの重量を拒否する() {
            assertThatThrownBy(() -> Weight.ofKilograms(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 負の重量を拒否する() {
            assertThatThrownBy(() -> Weight.ofKilograms(new BigDecimal("-0.001")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 境界値。DB は NUMERIC(10,3) であり、桁あふれは登録時ではなく保存時に落ちる。 */
        @Test
        void 小数第4位以下を持つ重量を拒否する() {
            assertThatThrownBy(() -> Weight.ofKilograms(new BigDecimal("1.0001")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 小数第3位までは受け入れる() {
            assertThatCode(() -> Weight.ofKilograms(new BigDecimal("0.001")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Dimensions（オプション）")
    class DimensionsTest {

        @Test
        void 縦横高がすべて正なら生成できる() {
            Dimensions d = Dimensions.ofCentimeters(
                    new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"));
            assertThat(d.length()).isEqualByComparingTo("120");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        void 正でない辺を拒否する(String invalid) {
            BigDecimal bad = new BigDecimal(invalid);
            BigDecimal ok = new BigDecimal("10");
            assertThatThrownBy(() -> Dimensions.ofCentimeters(bad, ok, ok))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Dimensions.ofCentimeters(ok, bad, ok))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Dimensions.ofCentimeters(ok, ok, bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * オプション項目は「未入力」と「不正な値」を区別する。
         *
         * <p>3 辺すべてが未入力なら寸法そのものが未入力であり、拒否ではない。
         * 一部だけ入力された状態は、入力途中の取りこぼしであり拒否する。
         */
        @Test
        void 三辺すべて未入力なら寸法なしとして扱う() {
            assertThat(Dimensions.ofNullableCentimeters(null, null, null)).isNull();
        }

        @Test
        void 一部だけ入力された寸法を拒否する() {
            assertThatThrownBy(() ->
                    Dimensions.ofNullableCentimeters(new BigDecimal("120"), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("寸法");
        }
    }

    @Nested
    @DisplayName("Quantity（オプション）")
    class QuantityTest {

        @Test
        void 個数1以上を受け入れる() {
            assertThat(Quantity.of(1).value()).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void 個数1未満を拒否する(int invalid) {
            assertThatThrownBy(() -> Quantity.of(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 未入力は個数なしとして扱う() {
            assertThat(Quantity.ofNullable(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Description（オプション）")
    class DescriptionTest {

        @Test
        void 品名500文字までを受け入れる() {
            assertThatCode(() -> Description.of("あ".repeat(500)))
                    .doesNotThrowAnyException();
        }

        @Test
        void 品名501文字を拒否する() {
            assertThatThrownBy(() -> Description.of("あ".repeat(501)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 空白だけの品名は入力されていないのと同じであり、品名なしとして扱う。 */
        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        void 空白のみは品名なしとして扱う(String blank) {
            assertThat(Description.ofNullable(blank)).isNull();
        }

        @Test
        void 前後の空白を取り除く() {
            assertThat(Description.of("  電子部品  ").value()).isEqualTo("電子部品");
        }
    }
}
