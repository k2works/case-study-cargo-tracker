package com.example.routingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 推奨順と費用の概算（US08・[ADR-018]）。
 *
 * <p>経路設計者は上から順に見る。並びが業務の判断と合っていなければ、一覧の意味が無い。
 */
@DisplayName("経路候補の推奨順と費用")
class RouteRecommendationTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location BUSAN = Location.of("KRPUS", "Busan");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private static Instant at(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    private static TransitEdge edge(String voyage, Location from, Location to,
            String departure, String arrival) {
        return TransitEdge.of(VoyageNumber.of(voyage), from, to, at(departure), at(arrival));
    }

    private static TransitPath direct(String arrival) {
        return TransitPath.of(List.of(
                edge("V-DIRECT", TOKYO, LOS_ANGELES, "2026-09-01T09:00:00Z", arrival)));
    }

    private static TransitPath viaBusan(String arrival) {
        return TransitPath.of(List.of(
                edge("V-A", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T09:00:00Z"),
                edge("V-B", BUSAN, LOS_ANGELES, "2026-09-04T09:00:00Z", arrival)));
    }

    private static TransitPath viaBusanAndShanghai(String arrival) {
        return TransitPath.of(List.of(
                edge("V-A", TOKYO, BUSAN, "2026-09-01T09:00:00Z", "2026-09-03T09:00:00Z"),
                edge("V-B", BUSAN, SHANGHAI, "2026-09-04T09:00:00Z", "2026-09-06T09:00:00Z"),
                edge("V-C", SHANGHAI, LOS_ANGELES, "2026-09-07T09:00:00Z", arrival)));
    }

    @Nested
    @DisplayName("推奨順")
    class Ranking {

        /** US08 の受入基準。直行便は最優先の候補として提示する。 */
        @Test
        @DisplayName("直行便は、遅く着いても最優先で提示する")
        void directComesFirstEvenIfSlower() {
            TransitPath slowDirect = direct("2026-09-25T09:00:00Z");
            TransitPath fastViaBusan = viaBusan("2026-09-18T09:00:00Z");

            List<TransitPath> ranked =
                    RouteRecommendation.rank(List.of(fastViaBusan, slowDirect));

            assertThat(ranked).containsExactly(slowDirect, fastViaBusan);
        }

        @Test
        @DisplayName("直行便どうしは、早く着く順に並べる")
        void directsOrderedByArrival() {
            TransitPath late = direct("2026-09-25T09:00:00Z");
            TransitPath early = direct("2026-09-20T09:00:00Z");

            assertThat(RouteRecommendation.rank(List.of(late, early)))
                    .containsExactly(early, late);
        }

        @Test
        @DisplayName("積み替えのある経路どうしは、早く着く順に並べる")
        void transshipmentsOrderedByArrival() {
            TransitPath late = viaBusan("2026-09-25T09:00:00Z");
            TransitPath early = viaBusanAndShanghai("2026-09-20T09:00:00Z");

            assertThat(RouteRecommendation.rank(List.of(late, early)))
                    .containsExactly(early, late);
        }

        /**
         * 到着が同じなら、積み替えの少ない経路を上にする。
         *
         * <p>荷役のたびに損傷と遅延の危険が上がる。同じ日に着くなら、触る回数の少ないほうがよい。
         */
        @Test
        @DisplayName("到着が同じなら、積み替えの少ない経路を上にする")
        void fewerTransshipmentsWinsOnATie() {
            TransitPath one = viaBusan("2026-09-20T09:00:00Z");
            TransitPath two = viaBusanAndShanghai("2026-09-20T09:00:00Z");

            assertThat(RouteRecommendation.rank(List.of(two, one))).containsExactly(one, two);
        }

        @Test
        @DisplayName("並べても候補は増えも減りもしない")
        void keepsEveryCandidate() {
            List<TransitPath> candidates =
                    List.of(viaBusan("2026-09-20T09:00:00Z"), direct("2026-09-22T09:00:00Z"));

            assertThat(RouteRecommendation.rank(candidates))
                    .containsExactlyInAnyOrderElementsOf(candidates);
        }
    }

    @Nested
    @DisplayName("費用の概算")
    class Cost {

        /**
         * 概算であり、請求される金額ではない。
         *
         * <p>運賃表も港湾利用料のマスタも存在しない（US21・IT11 まで）。ここで出すのは
         * 経路どうしを見比べるための目安であり、画面にもそう書く。
         */
        @Test
        @DisplayName("区間と経由港と輸送日数から概算する")
        void estimatesFromLegsPortsAndDays() {
            // 直行 1 区間・経由港 0・14 日
            TransitPath path = direct("2026-09-15T09:00:00Z");

            BigDecimal expected = new BigDecimal("200000")          // 区間 1 本
                    .add(new BigDecimal("30000").multiply(BigDecimal.valueOf(14)))  // 14 日
                    .add(new BigDecimal("50000").multiply(BigDecimal.valueOf(2)));  // 出発地と目的地

            assertThat(RouteRecommendation.estimatedCost(path)).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("積み替えが増えると、区間と港のぶん高くなる")
        void transshipmentCostsMore() {
            BigDecimal directCost = RouteRecommendation.estimatedCost(direct("2026-09-20T09:00:00Z"));
            BigDecimal viaBusanCost =
                    RouteRecommendation.estimatedCost(viaBusan("2026-09-20T09:00:00Z"));

            // 同じ日に着く経路どうしで比べる。差は区間 1 本と港 1 つぶん
            assertThat(viaBusanCost).isGreaterThan(directCost);
            assertThat(viaBusanCost.subtract(directCost))
                    .isEqualByComparingTo(new BigDecimal("250000"));
        }

        @Test
        @DisplayName("待つ日数が増えると高くなる")
        void longerTransitCostsMore() {
            assertThat(RouteRecommendation.estimatedCost(direct("2026-09-25T09:00:00Z")))
                    .isGreaterThan(RouteRecommendation.estimatedCost(direct("2026-09-15T09:00:00Z")));
        }
    }

    @Nested
    @DisplayName("候補が無いとき")
    class Empty {

        /** 候補が 0 件は正常な結果である。並べ替えも概算も、そこで落ちない。 */
        @Test
        @DisplayName("並べ替えても概算しても落ちない")
        void handlesNothingToRank() {
            assertThat(RouteRecommendation.rank(List.of())).isEmpty();
            assertThat(RouteRecommendation.rank(null)).isEmpty();
            assertThat(RouteRecommendation.estimatedCost(null)).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}