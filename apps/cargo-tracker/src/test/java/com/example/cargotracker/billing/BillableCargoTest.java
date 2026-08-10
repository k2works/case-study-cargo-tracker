package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.BillableCargo;
import com.example.cargotracker.billing.domain.model.CargoTypeFactor;
import com.example.cargotracker.billing.domain.model.FreightChargeCalculator;
import com.example.cargotracker.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 請求できる貨物かの判定と、基本料金の算出（US21）。
 *
 * <p><strong>「引取済」状態の予約に対して料金算出を開始できる</strong>（受入基準 1）。
 * ただし<strong>訂正・取り消しの申請中は開始できない</strong>。引取が取り消される
 * かもしれない貨物を請求すると、請求書を出した後で引取が無かったことになる。
 * <strong>US36 が「精算済みには申請できない」と定めた裏返しであり、
 * 両側から塞がないと隙間が残る</strong>（IT12 持ち越し C8）。
 *
 * <p><strong>基本料金に ADR-008 の概算式を使わない。</strong> 概算は経路候補の
 * 並べ替え用であり、荷主に見せた瞬間に請求額として読まれるため画面にも出していない。
 * <strong>並べ替えの物差しを請求に使ってはならない。</strong>
 */
@DisplayName("請求できる貨物と基本料金（US21）")
class BillableCargoTest {

    @Nested
    @DisplayName("請求できるか")
    class 請求できるか {

        @Test
        void 引取済みなら請求できる() {
            assertThat(new BillableCargo(true, false, false, true).isBillable()).isTrue();
        }

        /** 引取が済んでいない貨物は請求できない（受入基準 1）。 */
        @Test
        void 引取前は請求できない() {
            BillableCargo cargo = new BillableCargo(false, false, false, true);

            assertThat(cargo.isBillable()).isFalse();
            assertThat(cargo.reasonNotBillable()).contains("引取");
        }

        /**
         * <strong>訂正・取り消しの申請中は請求できない</strong>（C8 との接合点）。
         *
         * <p>取り消されるかもしれない引取をもとに請求書を出すと、
         * 出した後で引取が無かったことになる。
         */
        @Test
        void 訂正申請中は請求できない() {
            BillableCargo cargo = new BillableCargo(true, true, false, true);

            assertThat(cargo.isBillable()).isFalse();
            assertThat(cargo.reasonNotBillable()).contains("訂正");
        }

        /**
         * <strong>すでに請求済みなら二重に請求しない。</strong>
         *
         * <p>DB の一意制約（{@code invoice.booking_id}）でも防いでいるが、
         * <strong>制約に頼ると画面には 500 が出る</strong>。業務の言葉で拒む。
         */
        @Test
        void 請求済みなら再び請求できない() {
            BillableCargo cargo = new BillableCargo(true, false, true, true);

            assertThat(cargo.isBillable()).isFalse();
            assertThat(cargo.reasonNotBillable()).contains("請求");
        }

        /**
         * <strong>経路の記録が無い貨物は請求できない</strong>（レビュー H3）。
         *
         * <p>アダプタのコメントは「区間が 0 本の貨物は請求できない」と主張していたが、
         * <strong>その守りはどこにも実在しなかった</strong>。距離係数 0 が
         * {@code FreightChargeCalculator} まで届き、<strong>画面には 500 が出る</strong>。
         * <strong>制約に頼ると画面には 500 が出る</strong>と本クラス自身が書いた形そのものである。
         */
        @Test
        void 経路の記録が無い貨物は請求できない() {
            BillableCargo cargo = new BillableCargo(true, false, false, false);

            assertThat(cargo.isBillable()).isFalse();
            assertThat(cargo.reasonNotBillable()).contains("経路");
        }

        /** 請求できる貨物に理由は無い。**「理由がある = 請求できない」を一致させる。** */
        @Test
        void 請求できる貨物に理由は無い() {
            assertThat(new BillableCargo(true, false, false, true).reasonNotBillable()).isNull();
        }
    }

    @Nested
    @DisplayName("基本料金の算出")
    class 基本料金の算出 {

        /**
         * <strong>距離係数 × 重量 × 貨物種別係数</strong>
         * （{@code domain-model.md}「金額の丸め規則」の計算式）。
         */
        @Test
        void 一般貨物は係数一倍である() {
            Money charge = FreightChargeCalculator.calculate(
                    new BigDecimal("2"), new BigDecimal("1000"), CargoTypeFactor.GENERAL);

            assertThat(charge.value())
                    .as("距離係数 2 × 重量 1,000kg × 種別 1.0")
                    .isEqualTo(new BigDecimal("2000"));
        }

        /** 危険物は 1.8 倍。**取り扱いの手間とリスクが違う。** */
        @Test
        void 危険物は一点八倍である() {
            assertThat(FreightChargeCalculator.calculate(
                    new BigDecimal("2"), new BigDecimal("1000"), CargoTypeFactor.HAZARDOUS)
                    .value())
                    .isEqualTo(new BigDecimal("3600"));
        }

        /** 冷凍・冷蔵は 1.5 倍。**温度を保つ設備が要る。** */
        @Test
        void 冷凍は一点五倍である() {
            assertThat(FreightChargeCalculator.calculate(
                    new BigDecimal("2"), new BigDecimal("1000"), CargoTypeFactor.REFRIGERATED)
                    .value())
                    .isEqualTo(new BigDecimal("4500").subtract(new BigDecimal("1500")));
        }

        /**
         * <strong>算出した基本料金も丸める</strong>（段階丸めの 1 段目）。
         *
         * <p>丸めずに次段へ渡すと、割引と消費税の丸めが二重にずれる。
         */
        @Test
        void 基本料金の時点で丸める() {
            Money charge = FreightChargeCalculator.calculate(
                    new BigDecimal("1.7"), new BigDecimal("100.3"), CargoTypeFactor.GENERAL);

            assertThat(charge.value())
                    .as("1.7 × 100.3 = 170.51 → 切り捨て → 170")
                    .isEqualTo(new BigDecimal("170"));
        }

        /** <strong>重量ゼロの貨物は算出できない。</strong> 入力の誤りである。 */
        @Test
        void 重量がゼロなら算出できない() {
            assertThatThrownBy(() -> FreightChargeCalculator.calculate(
                    new BigDecimal("2"), BigDecimal.ZERO, CargoTypeFactor.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** <strong>距離係数ゼロも算出できない。</strong> 運んでいない貨物は請求できない。 */
        @Test
        void 距離係数がゼロなら算出できない() {
            assertThatThrownBy(() -> FreightChargeCalculator.calculate(
                    BigDecimal.ZERO, new BigDecimal("1000"), CargoTypeFactor.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
