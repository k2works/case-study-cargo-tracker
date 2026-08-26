package com.example.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * キャンセル料（US30-9・正典のビジネスルール 6）。
 *
 * <p><strong>IT6 から 3 イテレーション繰り越されてきた。</strong>IT6 は「US30 で一括して
 * 入れる」と書き、IT9 は「算定する場所が無い」として画面に「算定していません」と書いた。
 * ここで決着する。
 *
 * <p><strong>料率はキャンセルを申請した時点の状態で決まる。</strong>承認された時点では
 * ない——輸送中に申請したものは、承認が翌日でも輸送中の料率になる。
 */
@DisplayName("キャンセル料")
class CancellationFeeTest {

    private static final Money BASE = Money.yen(new BigDecimal("400000"));

    @Nested
    @DisplayName("料率")
    class Rates {

        /**
         * <strong>輸送開始後は高くなる</strong>（正典のビジネスルール 6）。
         *
         * <p>船に載せてから降ろすには実費がかかる。同じ料率にすると、
         * <strong>輸送中のキャンセルが実費を回収できない</strong>。
         */
        @Test
        @DisplayName("輸送中のキャンセルは、輸送開始前より高い")
        void chargesMoreAfterDeparture() {
            Money beforeDeparture = CancellationFee.forStatus(
                    CancelledAtStatus.CONFIRMED, BASE).amount();
            Money inTransit = CancellationFee.forStatus(
                    CancelledAtStatus.IN_TRANSIT, BASE).amount();

            assertThat(inTransit.amount())
                    .as("輸送中のキャンセルが輸送開始前と同じ料率になっている。実費を回収できない")
                    .isGreaterThan(beforeDeparture.amount());
        }

        /**
         * <strong>経路が決まる前のキャンセルは無料である。</strong>
         *
         * <p>まだ何も手配していない。料率を付けると、<strong>問い合わせの段階で
         * 引き返せなくなる</strong>。
         */
        @Test
        @DisplayName("経路が決まる前のキャンセルは無料である")
        void chargesNothingBeforeAnythingIsArranged() {
            assertThat(CancellationFee.forStatus(CancelledAtStatus.PRELIMINARY, BASE).amount())
                    .isEqualTo(Money.zero());
            assertThat(CancellationFee.forStatus(CancelledAtStatus.ROUTE_PROPOSED, BASE).amount())
                    .isEqualTo(Money.zero());
        }

        /**
         * <strong>すべての状態が料率を持つ</strong>（Try 3 の一般形）。
         *
         * <p>決め忘れた状態があると、その状態でキャンセルされた貨物だけ
         * <strong>キャンセル料が算定されない</strong>——請求漏れになる。
         */
        @ParameterizedTest
        @EnumSource(CancelledAtStatus.class)
        @DisplayName("すべての状態が、料率を持つ")
        void everyStatusHasARate(CancelledAtStatus status) {
            CancellationFee fee = CancellationFee.forStatus(status, BASE);

            assertThat(fee.feeRate())
                    .as("%s の料率が決まっていない。この状態のキャンセルだけ請求漏れになる", status)
                    .isNotNull();
            assertThat(fee.amount()).isNotNull();
        }

        /**
         * <strong>値を足したら、この検査が赤になる。</strong>
         *
         * <p>bookingms の {@code BookingStatus} は 8 値だが、<strong>キャンセルできるのは
         * 6 値</strong>——配送完了とキャンセル済みからはキャンセルできない。
         */
        @Test
        @DisplayName("キャンセルできる状態は、いま 6 つある")
        void hasTheAgreedValues() {
            assertThat(Arrays.stream(CancelledAtStatus.values()).map(Enum::name))
                    .as("キャンセルできる状態が増減した。**料率を決めること**")
                    .containsExactly("PRELIMINARY", "ROUTE_PROPOSED", "ROUTE_NOTIFIED",
                            "CONFIRMED", "TRACKING_ISSUED", "IN_TRANSIT");
        }
    }

    @Nested
    @DisplayName("算定の根拠")
    class Evidence {

        /**
         * <strong>算定根拠（状態・料率）を持つ</strong>（正典のビジネスルール 6）。
         *
         * <p>金額だけ残ると、荷主から問われたときに<strong>なぜその額かを言えない</strong>。
         */
        @Test
        @DisplayName("キャンセル時の状態と料率を持つ")
        void keepsTheBasisOfTheRate() {
            CancellationFee fee = CancellationFee.forStatus(CancelledAtStatus.IN_TRANSIT, BASE);

            assertThat(fee.bookingStatusAtCancel()).isEqualTo(CancelledAtStatus.IN_TRANSIT);
            assertThat(fee.feeRate()).isEqualByComparingTo("0.3");
            assertThat(fee.amount()).isEqualTo(Money.yen(new BigDecimal("120000")));
        }

        /** 端数は 1 円に丸まる（決定 2）。 */
        @Test
        @DisplayName("料率をかけた端数は 1 円に丸まる")
        void roundsTheFee() {
            CancellationFee fee = CancellationFee.forStatus(
                    CancelledAtStatus.IN_TRANSIT, Money.yen(new BigDecimal("12345")));

            assertThat(fee.amount()).isEqualTo(Money.yen(new BigDecimal("3704")));
        }
    }

    @Nested
    @DisplayName("成り立たない入力")
    class InvalidInput {

        @Test
        @DisplayName("状態が無ければ算定できない")
        void requiresAStatus() {
            assertThatThrownBy(() -> CancellationFee.forStatus(null, BASE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("基本料金が無ければ算定できない")
        void requiresABaseAmount() {
            assertThatThrownBy(() ->
                    CancellationFee.forStatus(CancelledAtStatus.IN_TRANSIT, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * <strong>キャンセルできない状態を断る。</strong>
         *
         * <p>配送完了・キャンセル済みからはキャンセルできない。通すと、
         * <strong>すでに運び終えた貨物にキャンセル料が乗る</strong>。
         */
        @Test
        @DisplayName("キャンセルできない状態は断る")
        void rejectsStatusesThatCannotBeCancelled() {
            assertThatThrownBy(() -> CancelledAtStatus.of("DELIVERED"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CancelledAtStatus.of("CANCELLED"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CancelledAtStatus.of("SOMETHING_NEW"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
