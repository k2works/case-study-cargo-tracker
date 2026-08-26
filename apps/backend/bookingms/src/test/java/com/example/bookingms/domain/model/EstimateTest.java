package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 輸送見積（US01）。
 *
 * <p><strong>見積は予約ではない。</strong>作っても貨物は動かない。それでも荷主に
 * 出す数字であり、<strong>実料金と違ってはならない</strong>（[ADR-028] 決定 6）。
 */
@DisplayName("輸送見積")
class EstimateTest {

    private static final EstimateId ID = EstimateId.generate();
    private static final EstimateNumber NUMBER = EstimateNumber.of("EST-2026000001");
    private static final LocalDate DEADLINE = LocalDate.parse("2027-12-31");

    private static Estimate create(List<RouteCandidate> candidates) {
        return Estimate.create(ID, NUMBER, "JPTYO", "USLAX", DEADLINE, CargoType.GENERAL,
                new BigDecimal("4200"), candidates);
    }

    @Nested
    @DisplayName("作成")
    class Creating {

        @Test
        @DisplayName("5 項目と候補を持って作られる")
        void keepsTheRequirementsAndCandidates() {
            Estimate estimate = create(List.of(
                    new RouteCandidate("V001", null, 12, new BigDecimal("300000"))));

            assertThat(estimate.originUnLocode()).isEqualTo("JPTYO");
            assertThat(estimate.destinationUnLocode()).isEqualTo("USLAX");
            assertThat(estimate.arrivalDeadline()).isEqualTo(DEADLINE);
            assertThat(estimate.cargoType()).isEqualTo(CargoType.GENERAL);
            assertThat(estimate.weightKg()).isEqualByComparingTo("4200");
            assertThat(estimate.candidates()).hasSize(1);
            assertThat(estimate.status()).isEqualTo(EstimateStatus.CREATED);
        }

        /** <strong>候補が無くても見積は作れる</strong>（受入基準 01-5）。 */
        @Test
        @DisplayName("候補が 1 件も無くても作れる")
        void allowsAnEstimateWithoutCandidates() {
            assertThat(create(List.of()).candidates()).isEmpty();
        }

        /** 渡した一覧をあとから書き換えても、見積の中身は変わらない。 */
        @Test
        @DisplayName("渡した候補の一覧を書き換えても、見積は変わらない")
        void copiesTheCandidates() {
            List<RouteCandidate> given = new java.util.ArrayList<>(List.of(
                    new RouteCandidate("V001", null, 12, new BigDecimal("300000"))));
            Estimate estimate = create(given);

            given.clear();

            assertThat(estimate.candidates()).hasSize(1);
        }

        @Test
        @DisplayName("同じ港へは運べない")
        void rejectsTheSameOriginAndDestination() {
            assertThatThrownBy(() -> Estimate.create(ID, NUMBER, "JPTYO", "JPTYO", DEADLINE,
                    CargoType.GENERAL, new BigDecimal("4200"), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("重量が 0 以下なら断る")
        void rejectsNonPositiveWeight() {
            assertThatThrownBy(() -> Estimate.create(ID, NUMBER, "JPTYO", "USLAX", DEADLINE,
                    CargoType.GENERAL, BigDecimal.ZERO, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("予約との突き合わせ（受入基準 01-7・US04 の未達）")
    class ComparingWithABooking {

        private final Estimate estimate = create(List.of());

        /** <strong>同じ条件なら何も言わない。</strong>言うと、毎回の予約に警告が出る。 */
        @Test
        @DisplayName("同じ条件なら食い違いは無い")
        void reportsNoDifferenceForTheSameRequirements() {
            assertThat(estimate.differencesFrom("JPTYO", "USLAX", DEADLINE, CargoType.GENERAL,
                    new BigDecimal("4200"))).isEmpty();
        }

        /** <strong>桁数ではなく値で比べる。</strong>4200 と 4200.000 は同じ重量である。 */
        @Test
        @DisplayName("重量の桁数が違うだけなら、食い違いとは言わない")
        void comparesWeightByValue() {
            assertThat(estimate.differencesFrom("JPTYO", "USLAX", DEADLINE, CargoType.GENERAL,
                    new BigDecimal("4200.000"))).isEmpty();
        }

        /**
         * <strong>食い違った項目を名前で返す。</strong>「違います」だけでは、
         * 営業担当者は荷主に何を確かめればよいか分からない。
         */
        @Test
        @DisplayName("食い違った項目を名前で返す")
        void namesTheDifferences() {
            assertThat(estimate.differencesFrom("JPYOK", "USLAX", DEADLINE, CargoType.HAZARDOUS,
                    new BigDecimal("99000")))
                    .containsExactlyInAnyOrder("出発地", "貨物種別", "重量");
        }

        /** **5 項目すべてを見る**（1 つでも見落とすと、その項目だけ黙って通る）。 */
        @Test
        @DisplayName("5 項目すべてが突き合わせの対象である")
        void comparesEveryRequirement() {
            assertThat(estimate.differencesFrom("JPOSA", "CNSHA",
                    LocalDate.parse("2028-01-31"), CargoType.REFRIGERATED,
                    new BigDecimal("1")))
                    .containsExactlyInAnyOrder("出発地", "目的地", "到着期限", "貨物種別", "重量");
        }

        /** 重量が渡らないときも食い違いとして扱う（黙って通さない）。 */
        @Test
        @DisplayName("重量が渡らなければ食い違いとして扱う")
        void treatsAMissingWeightAsADifference() {
            assertThat(estimate.differencesFrom("JPTYO", "USLAX", DEADLINE, CargoType.GENERAL,
                    null)).containsExactly("重量");
        }
    }

    @Nested
    @DisplayName("識別子と見積番号（[ADR-028] 決定 7）")
    class Identifiers {

        /** <strong>UUID である。</strong>連番だけだと、隣の見積が開ける。 */
        @Test
        @DisplayName("見積の識別子は UUID の形をしている")
        void estimateIdIsAUuid() {
            assertThat(EstimateId.of("3f2504e0-4f89-11d3-9a0c-0305e82c3301").value())
                    .isEqualTo(java.util.UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
        }

        /**
         * <strong>形が違えば断る。</strong>「見つかりません」に化けさせない
         * ——解析の失敗と、存在しない見積は別である。
         */
        @Test
        @DisplayName("識別子の形が違えば断る")
        void rejectsMalformedIds() {
            assertThatThrownBy(() -> EstimateId.of("not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 人が読む番号は `EST-YYYY` + 6 桁（[ADR-011] と同じ形）。 */
        @Test
        @DisplayName("見積番号は EST-YYYY と 6 桁である")
        void estimateNumberHasTheAgreedShape() {
            assertThat(EstimateNumber.of("EST-2026000001").value()).isEqualTo("EST-2026000001");
            assertThatThrownBy(() -> EstimateNumber.of("EST-1"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> EstimateNumber.of("INV-2026000001"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("ルート候補（受入基準 01-3）")
    class Candidates {

        /** <strong>4 項目を持つ。</strong>1 つ欠けても字面は満たす（IT11 Try 2）。 */
        @Test
        @DisplayName("候補は航海番号・経由港・所要日数・概算料金を持つ")
        void hasTheFourFields() {
            RouteCandidate candidate =
                    new RouteCandidate("V001", "SGSIN", 21, new BigDecimal("420000"));

            assertThat(candidate.voyageNumber()).isEqualTo("V001");
            assertThat(candidate.transitPort()).isEqualTo("SGSIN");
            assertThat(candidate.transitDays()).isEqualTo(21);
            assertThat(candidate.estimatedCost()).isEqualByComparingTo("420000");
            assertThat(candidate.direct()).isFalse();
        }

        /** 直行は経由港を持たない。**空文字は「経由港なし」と同じに倒す。** */
        @Test
        @DisplayName("直行は経由港を持たない")
        void directCandidatesHaveNoTransitPort() {
            assertThat(new RouteCandidate("V001", null, 12, new BigDecimal("300000")).direct())
                    .isTrue();
            assertThat(new RouteCandidate("V001", " ", 12, new BigDecimal("300000")).direct())
                    .isTrue();
        }

        @Test
        @DisplayName("航海番号の無い候補・負の値は断る")
        void rejectsInvalidCandidates() {
            assertThatThrownBy(() -> new RouteCandidate(" ", null, 1, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RouteCandidate("V001", null, -1, BigDecimal.ONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RouteCandidate("V001", null, 1, new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
