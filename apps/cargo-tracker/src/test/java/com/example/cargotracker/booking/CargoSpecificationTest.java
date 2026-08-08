package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.TemperatureUnit;
import com.example.cargotracker.booking.domain.model.Weight;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 貨物仕様の不変条件（US05）。
 *
 * <p><strong>守りは「入口が 1 つである」ことに依存する。</strong> IT9 では
 * {@code create} に「申告の無い危険物を預からない」を入れたが、
 * {@code of} と {@code reconstruct} という別の入口が残っており、
 * そこを通れば申告の無い危険物が作れた（IT9 レビュー M2）。
 * 画面からの経路は {@code create} を通るため、統合テストでは見つからない。
 *
 * <p>本テストは<strong>入口ごとに</strong>守りの有無を明示する。
 */
@DisplayName("貨物仕様の不変条件")
class CargoSpecificationTest {

    private static final Weight 重量 = Weight.ofKilograms(BigDecimal.valueOf(100));

    private static final HazardousDeclaration 危険物申告 =
            new HazardousDeclaration("3", "UN1263", "PAINT");

    private static final TemperatureRequirement 温度条件 =
            new TemperatureRequirement(
                    BigDecimal.valueOf(-20), BigDecimal.valueOf(-5), TemperatureUnit.CELSIUS);

    @Nested
    @DisplayName("新しく預かるとき（create）")
    class 新しく預かるとき {

        @Test
        void 申告の無い危険物は預からない() {
            assertThatThrownBy(() -> CargoSpecification.create(
                    CargoType.HAZARDOUS, 重量, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物申告");
        }

        @Test
        void 温度条件の無い冷凍貨物は預からない() {
            assertThatThrownBy(() -> CargoSpecification.create(
                    CargoType.REFRIGERATED, 重量, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("温度管理条件");
        }

        @Test
        void 申告のある危険物は預かれる() {
            var spec = CargoSpecification.create(
                    CargoType.HAZARDOUS, 重量, null, null, null, 危険物申告, null);

            assertThat(spec.hasHazardousDeclaration()).isTrue();
        }

        /** <strong>種別を変えた後に残った入力は捨てる。</strong> */
        @Test
        void 一般貨物に付いてきた危険物申告は捨てる() {
            var spec = CargoSpecification.create(
                    CargoType.GENERAL, 重量, null, null, null, 危険物申告, 温度条件);

            assertThat(spec.hasHazardousDeclaration()).isFalse();
            assertThat(spec.hasTemperatureRequirement()).isFalse();
        }
    }

    /**
     * <strong>簡便な入口も同じ守りを通る</strong>（IT9 レビュー M2 の返済）。
     *
     * <p>{@code of} は「必須項目だけを持つ仕様」を作る近道であり、テストの
     * 組み立てで多用される。ここが {@code create} を迂回していると、
     * <strong>申告の無い危険物を前提にしたテストが書け、しかも緑になる</strong>。
     * 集約の守りがテストの世界でだけ無効になるのが最も危うい。
     */
    @Nested
    @DisplayName("必須項目だけの近道（of）")
    class 必須項目だけの近道 {

        @Test
        void 一般貨物は作れる() {
            assertThat(CargoSpecification.of(CargoType.GENERAL, 重量).cargoType())
                    .isEqualTo(CargoType.GENERAL);
        }

        @Test
        void 危険物は作れない() {
            assertThatThrownBy(() -> CargoSpecification.of(CargoType.HAZARDOUS, 重量))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("危険物申告");
        }

        @Test
        void 冷凍貨物は作れない() {
            assertThatThrownBy(() -> CargoSpecification.of(CargoType.REFRIGERATED, 重量))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("温度管理条件");
        }
    }

    /**
     * <strong>復元だけは整合を求めない。</strong>
     *
     * <p>危険物・温度の列が無かったころ（IT1〜IT8）に登録された予約には申告が無い。
     * 復元で整合を求めると<strong>保存できたものが読めなくなり</strong>、
     * その予約の追跡もキャンセルもできなくなる。
     *
     * <p>これは「守りが緩い」のではなく<strong>守る時点が違う</strong>。
     * 新しく預かるときには通さない。
     */
    @Nested
    @DisplayName("永続化からの復元（reconstruct）")
    class 永続化からの復元 {

        @Test
        void 申告の無い危険物も読み戻せる() {
            var spec = CargoSpecification.reconstruct(
                    CargoType.HAZARDOUS, 重量, null, null, null, null, null);

            assertThat(spec.cargoType()).isEqualTo(CargoType.HAZARDOUS);
            assertThat(spec.hasHazardousDeclaration()).isFalse();
        }

        @Test
        void 温度条件の無い冷凍貨物も読み戻せる() {
            var spec = CargoSpecification.reconstruct(
                    CargoType.REFRIGERATED, 重量, null, null, null, null, null);

            assertThat(spec.hasTemperatureRequirement()).isFalse();
        }

        /** 復元でも「種別に合わない値」は捨てる（読めない形を作らないため）。 */
        @Test
        void 一般貨物に付いていた危険物申告は捨てる() {
            var spec = CargoSpecification.reconstruct(
                    CargoType.GENERAL, 重量, null, null, null, 危険物申告, null);

            assertThat(spec.hasHazardousDeclaration()).isFalse();
        }
    }
}
