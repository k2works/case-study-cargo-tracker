package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 金額と丸め規則（US21。{@code domain-model.md}「金額の丸め規則」）。
 *
 * <p><strong>金額計算は法的・会計的な争いの対象になりうる。</strong> 丸めの規則と
 * 適用順序を仕様として固定する。順序が決まっていないと、
 * <strong>同じ入力でも実装者によって請求額が変わる</strong>。
 *
 * <p><strong>最小通貨単位の整数で保持する。</strong> `data-model.md` の
 * {@code *_value} は INTEGER であり、`NUMERIC` を使わない判断（判断 3）に従う。
 * 丸める直前までは {@code BigDecimal}（スケール 10 以上）で持ち、{@code double} を使わない。
 *
 * <p><strong>Routing の {@code Money} とは別の型である。</strong> 概算費用（ADR-008）は
 * 経路候補の並べ替え用であり、請求額ではない。BC をまたいで型を共有すると、
 * <strong>並べ替えの物差しが請求に流れ込む</strong>（ADR-005）。
 */
@DisplayName("金額と丸め規則（US21）")
class MoneyTest {

    @Nested
    @DisplayName("生成")
    class 生成 {

        @Test
        void 最小通貨単位の整数で保持する() {
            assertThat(Money.yen(new BigDecimal("85002.55")).value())
                    .as("切り捨て。荷主に不利な方向へ丸めない")
                    .isEqualTo(new BigDecimal("85002"));
        }

        @Test
        void 通貨を必ず伴う() {
            assertThat(Money.yen(BigDecimal.TEN).currency()).isEqualTo("JPY");
        }

        /** <strong>負の金額は請求ではない。</strong> 返金は別の業務である。 */
        @Test
        void 負の金額は作れない() {
            assertThatThrownBy(() -> Money.yen(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("丸め規則")
    class 丸め規則 {

        /**
         * <strong>切り捨てる</strong>（{@code RoundingMode.DOWN}）。
         *
         * <p>荷主に不利な方向へ丸めない。四捨五入だと、1 円の切り上げが
         * 積み重なって「請求が多い」という指摘を受ける。
         */
        @Test
        void 切り捨てる() {
            assertThat(Money.yen(new BigDecimal("100.99")).value())
                    .isEqualTo(new BigDecimal("100"));
        }

        /**
         * <strong>段階丸めである。</strong> 基本料金・割引後料金・消費税額の
         * それぞれで丸める。総額での一括丸めは行わない。
         *
         * <p>{@code domain-model.md} の計算例をそのまま固定する。
         * <strong>実装より先に例を決める</strong> — 実装で決めると、
         * 例が実装の写しになって検算にならない。
         */
        @Test
        void 設計書の計算例と一致する() {
            Money base = Money.yen(new BigDecimal("100003"));

            Money discounted = base.multiply(new BigDecimal("0.85"));
            assertThat(discounted.value())
                    .as("100,003 × 0.85 = 85,002.55 → 切り捨て → 85,002")
                    .isEqualTo(new BigDecimal("85002"));

            Money tax = discounted.multiply(new BigDecimal("0.10"));
            assertThat(tax.value())
                    .as("85,002 × 0.10 = 8,500.2 → 切り捨て → 8,500")
                    .isEqualTo(new BigDecimal("8500"));

            assertThat(discounted.add(tax).value())
                    .as("85,002 + 8,500 = 93,502")
                    .isEqualTo(new BigDecimal("93502"));
        }

        /**
         * <strong>順序を変えると結果が変わる。</strong>
         *
         * <p>「割引 → 丸め → 課税 → 丸め」と「割引 → 課税 → 一括丸め」では 1 円ずれる。
         * <strong>この差が、順序を仕様として固定する理由そのものである。</strong>
         * ずれないなら固定する必要が無い。
         *
         * <p><strong>設計書の計算例（100,003 円）では差が出ない。</strong>
         * 例で確かめただけでは「順序を決める必要がある」ことを示せないため、
         * <strong>実際にずれる値を探して固定した</strong>（100,007 円で 93,505 と 93,506）。
         */
        @Test
        void 順序を変えると結果が変わることを示す() {
            BigDecimal base = new BigDecimal("100007");

            // 仕様どおり: 割引で丸め、税で丸める
            Money specified = Money.yen(base).multiply(new BigDecimal("0.85"));
            Money specifiedTotal = specified.add(specified.multiply(new BigDecimal("0.10")));

            // 一括丸め（やってはならない形）
            BigDecimal lumped = base
                    .multiply(new BigDecimal("0.85"))
                    .multiply(new BigDecimal("1.10"))
                    .setScale(0, java.math.RoundingMode.DOWN);

            assertThat(specifiedTotal.value())
                    .as("段階丸め 93,505 円")
                    .isEqualTo(new BigDecimal("93505"));
            assertThat(lumped)
                    .as("一括丸め 93,506 円。1 円多く請求することになる")
                    .isEqualTo(new BigDecimal("93506"));
        }

        /** <strong>{@code double} を使わない。</strong> 中間計算の精度を落とさない。 */
        @Test
        void 中間計算で精度を落とさない() {
            // 0.1 は double で正確に表せない。BigDecimal で持てば桁落ちしない
            Money result = Money.yen(new BigDecimal("1000000007"))
                    .multiply(new BigDecimal("0.10"));
            assertThat(result.value()).isEqualTo(new BigDecimal("100000000"));
        }
    }

    @Nested
    @DisplayName("加算")
    class 加算 {

        @Test
        void 同じ通貨どうしを足せる() {
            assertThat(Money.yen(new BigDecimal("100")).add(Money.yen(new BigDecimal("23"))).value())
                    .isEqualTo(new BigDecimal("123"));
        }

        /**
         * <strong>通貨が違えば足せない。</strong>
         *
         * <p>多通貨は保留（`release_scope.md`）だが、<strong>型が黙って足すと
         * 保留のはずのものが動いてしまう</strong>。足せないことを明示する。
         */
        @Test
        void 通貨が違えば足せない() {
            Money jpy = Money.yen(new BigDecimal("100"));
            Money usd = new Money(new BigDecimal("100"), "USD");
            assertThatThrownBy(() -> jpy.add(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("通貨");
        }
    }

    @Nested
    @DisplayName("減算")
    class 減算 {

        /** 料金調整（減額）に使う（US21 の受入基準 6）。 */
        @Test
        void 同じ通貨どうしを引ける() {
            assertThat(Money.yen(new BigDecimal("100"))
                    .subtract(Money.yen(new BigDecimal("30"))).value())
                    .isEqualTo(new BigDecimal("70"));
        }

        /**
         * <strong>差し引いて負になる減額は認めない。</strong>
         *
         * <p>請求額を超える減額は「返金」であり、精算の取り消しを伴う別の業務である
         * （`release_scope.md` のスコープ外。Release 2.0 で判断する）。
         * <strong>黙って負の請求書を作らない。</strong>
         */
        @Test
        void 請求額を超える減額は認めない() {
            Money base = Money.yen(new BigDecimal("100"));
            Money tooMuch = Money.yen(new BigDecimal("101"));
            assertThatThrownBy(() -> base.subtract(tooMuch))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
