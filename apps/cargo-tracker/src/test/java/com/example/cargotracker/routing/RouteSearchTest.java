package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.FreightEstimator;
import com.example.cargotracker.routing.domain.model.ProposedRoute;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 経路探索（US08）。<strong>ドメインの不変条件をここで固める。</strong>
 *
 * <p>画面から作ると「つながっていない経路」「期限を過ぎる経路」を候補として
 * 出してしまう。探索と評価が正しいことは、外側を作る前に確かめる。
 */
@DisplayName("経路候補の算出（US08）")
class RouteSearchTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /** 概算費用の単価。**テストが式そのものを固定しないよう、値は素直に置く。** */
    private final FreightEstimator estimator =
            new FreightEstimator(new BigDecimal("100"), new BigDecimal("1.5"));

    private final RouteSearchService search = new RouteSearchService(estimator, JST);

    private Voyage 航海(String number, Set<RoutingCargoType> types, String... legs) {
        // legs は "港,出発,到着港,到着" の 4 要素ずつ
        List<CarrierMovement> movements = new java.util.ArrayList<>();
        for (int i = 0; i < legs.length; i += 4) {
            movements.add(CarrierMovement.of(
                    Location.of(legs[i]), Location.of(legs[i + 2]),
                    Instant.parse(legs[i + 1]), Instant.parse(legs[i + 3])));
        }
        return Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"),
                new CarrierName("日本海運"), Schedule.of(movements), types));
    }

    private RoutingCriteria 条件(String origin, String destination, String deadline) {
        return RoutingCriteria.of(
                Location.of(origin), Location.of(destination),
                LocalDate.parse(deadline), RoutingCargoType.GENERAL,
                RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
    }

    private Voyage 大阪発ロサンゼルス行き直行便() {
        return 航海("V001", Set.of(RoutingCargoType.GENERAL),
                "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-14T06:00:00Z");
    }

    /**
     * 条件の無い探索を拒否する。
     *
     * <p><strong>黙って空を返さない。</strong> 空を返すと「候補ゼロ」と
     * 区別がつかず、便が無いのか条件が渡っていないのかが分からなくなる。
     */
    @Test
    void 条件が無ければ探索できない() {
        assertThatThrownBy(() -> search.search(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> search.search(条件("JPOSA", "USLAX", "2026-10-20"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("探索")
    class 探索 {

        /** 受入基準: 出発地・目的地・期限を入力として候補が算出される。 */
        @Test
        void 出発地から目的地へ行ける航海が候補になる() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-20"),
                    List.of(大阪発ロサンゼルス行き直行便()));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.voyageNumber().value()).isEqualTo("V001"));
        }

        /** 目的地に立ち寄らない航海は候補にならない。 */
        @Test
        void 目的地へ行かない航海は候補にならない() {
            var routes = search.search(条件("JPOSA", "DEHAM", "2026-12-31"),
                    List.of(大阪発ロサンゼルス行き直行便()));

            assertThat(routes).isEmpty();
        }

        /**
         * 受入基準: 寄港地の接続可能性が評価される。
         *
         * <p><strong>途中の港から乗ることもできる。</strong> 航海の出発港が
         * 貨物の出発地と一致する必要はない。
         */
        @Test
        void 寄港地から乗り寄港地で降りられる() {
            var voyage = 航海("V002", Set.of(RoutingCargoType.GENERAL),
                    "JPYOK", "2026-10-01T10:00:00Z", "SGSIN", "2026-10-08T06:00:00Z",
                    "SGSIN", "2026-10-09T10:00:00Z", "DEHAM", "2026-10-28T06:00:00Z");

            var routes = search.search(条件("SGSIN", "DEHAM", "2026-11-30"), List.of(voyage));

            assertThat(routes).singleElement()
                    .satisfies(r -> {
                        assertThat(r.departureTime())
                                .isEqualTo(Instant.parse("2026-10-09T10:00:00Z"));
                        assertThat(r.arrivalTime())
                                .isEqualTo(Instant.parse("2026-10-28T06:00:00Z"));
                    });
        }

        /**
         * <strong>逆走はしない。</strong> 目的地に着いた後に出発地へ寄る航海は使えない。
         *
         * <p>この航海は目的地（USLAX）に着いてから出発地（JPOSA）を出る。
         * 乗る港と降りる港が<strong>どちらも航路上にある</strong>ため、順序を見ないと
         * 候補として通ってしまい、<strong>到着が出発より前の候補</strong>が生まれる。
         */
        @Test
        void 目的地に着いた後に出発地を出る航海は候補にならない() {
            var voyage = 航海("V003", Set.of(RoutingCargoType.GENERAL),
                    "CNSHA", "2026-10-01T00:00:00Z", "USLAX", "2026-10-14T00:00:00Z",
                    "USLAX", "2026-10-15T00:00:00Z", "JPOSA", "2026-10-28T00:00:00Z",
                    "JPOSA", "2026-10-29T00:00:00Z", "SGSIN", "2026-11-05T00:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-12-31"), List.of(voyage));

            assertThat(routes).isEmpty();
        }

        /** 出発地に立ち寄らない航海は候補にならない。 */
        @Test
        void 出発地へ立ち寄らない航海は候補にならない() {
            var voyage = 航海("V005", Set.of(RoutingCargoType.GENERAL),
                    "USLAX", "2026-10-01T10:00:00Z", "JPOSA", "2026-10-14T06:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-12-31"), List.of(voyage));

            assertThat(routes).isEmpty();
        }

        /** 経由した港が候補に載る。**「なぜ遅いのか」は経由で説明できる。** */
        @Test
        void 経由港が候補に載る() {
            var voyage = 航海("V004", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "CNSHA", "2026-10-03T06:00:00Z",
                    "CNSHA", "2026-10-04T10:00:00Z", "USLAX", "2026-10-18T06:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-31"), List.of(voyage));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.transitPorts())
                            .extracting(Location::unlocode).containsExactly("CNSHA"));
        }
    }

    @Nested
    @DisplayName("枝刈り（経由回数の上限）")
    class 枝刈り {

        private Voyage 三回経由する航海() {
            return 航海("V010", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T00:00:00Z", "CNSHA", "2026-10-02T00:00:00Z",
                    "CNSHA", "2026-10-03T00:00:00Z", "HKHKG", "2026-10-04T00:00:00Z",
                    "HKHKG", "2026-10-05T00:00:00Z", "SGSIN", "2026-10-06T00:00:00Z",
                    "SGSIN", "2026-10-07T00:00:00Z", "USLAX", "2026-10-20T00:00:00Z");
        }

        /** 上限（既定 2）を超える経由の航海は候補にしない。 */
        @Test
        void 経由回数の上限を超える航海は候補にならない() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-31"),
                    List.of(三回経由する航海()));

            assertThat(routes).isEmpty();
        }

        /** <strong>ちょうど上限は通す。</strong> 境界で 1 つずれると候補が消える。 */
        @Test
        void 経由回数がちょうど上限なら候補になる() {
            var criteria = RoutingCriteria.of(
                    Location.of("JPOSA"), Location.of("USLAX"),
                    LocalDate.parse("2026-10-31"), RoutingCargoType.GENERAL,
                    RoutingWeight.ofKilograms(new BigDecimal("1000")), 3);

            var routes = search.search(criteria, List.of(三回経由する航海()));

            assertThat(routes).hasSize(1);
        }

        /** 経由回数の上限は負にできない。 */
        @Test
        void 経由回数の上限が負なら拒否する() {
            assertThatThrownBy(() -> RoutingCriteria.of(
                    Location.of("JPOSA"), Location.of("USLAX"),
                    LocalDate.parse("2026-10-31"), RoutingCargoType.GENERAL,
                    RoutingWeight.ofKilograms(BigDecimal.ONE), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("期限の判定")
    class 期限の判定 {

        /**
         * <strong>期限の判定は日付単位で行う</strong>（{@code domain-model.md} ルール 2-1）。
         *
         * <p>期限は時刻を持たない日付であり、到着は時刻を持つ。素朴に比べると
         * 期限が 0 時として扱われ、<strong>期限当日に着く便がすべて期限超過になる</strong>。
         */
        @Test
        void 期限当日の時刻付き到着は期限内とする() {
            // 日本時間 2026-10-14 23:59 着（UTC では 14:59）
            var voyage = 航海("V020", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-14T14:59:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-14"), List.of(voyage));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.deadlineSatisfied()).isTrue());
        }

        /** 翌日に着く便は期限超過である。 */
        @Test
        void 期限翌日の到着は期限超過とする() {
            var voyage = 航海("V021", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-15T01:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-14"), List.of(voyage));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.deadlineSatisfied()).isFalse());
        }

        /**
         * 期限を過ぎる便も<strong>候補には残す</strong>。
         *
         * <p>消してしまうと「なぜ候補が無いのか」が分からなくなる。
         * 期限を延ばせば使えることは、見えていて初めて判断できる（US10）。
         */
        @Test
        void 期限を過ぎる便も候補に残る() {
            var voyage = 航海("V022", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-11-30T06:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-14"), List.of(voyage));

            assertThat(routes).hasSize(1);
        }
    }

    @Nested
    @DisplayName("貨物種別の取扱")
    class 貨物種別の取扱 {

        private Voyage 一般貨物しか運べない航海() {
            return 航海("V030", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-14T06:00:00Z");
        }

        private RoutingCriteria 危険物の条件() {
            return RoutingCriteria.of(
                    Location.of("JPOSA"), Location.of("USLAX"),
                    LocalDate.parse("2026-10-31"), RoutingCargoType.HAZARDOUS,
                    RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
        }

        /**
         * 運べない便も<strong>一覧から消さない</strong>（ビジネスルール 6）。
         *
         * <p>消すと「なぜあの便が出てこないのか」を確認できなくなる。
         */
        @Test
        void 運べない便も候補に残り選べない() {
            var routes = search.search(危険物の条件(), List.of(一般貨物しか運べない航海()));

            assertThat(routes).singleElement().satisfies(r -> {
                assertThat(r.selectable()).isFalse();
                assertThat(r.unselectableReason()).contains("危険物");
            });
        }

        /** 運べる便は選べる。 */
        @Test
        void 運べる便は選べる() {
            var voyage = 航海("V031",
                    Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-14T06:00:00Z");

            var routes = search.search(危険物の条件(), List.of(voyage));

            assertThat(routes).singleElement().satisfies(r -> {
                assertThat(r.selectable()).isTrue();
                assertThat(r.unselectableReason()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("推奨順")
    class 推奨順 {

        private final Voyage directVoyage = 航海("V-DIRECT", Set.of(RoutingCargoType.GENERAL),
                "JPOSA", "2026-10-01T00:00:00Z", "USLAX", "2026-10-15T00:00:00Z");

        private final Voyage fasterViaVoyage = 航海("V-FAST", Set.of(RoutingCargoType.GENERAL),
                "JPOSA", "2026-10-01T00:00:00Z", "CNSHA", "2026-10-02T00:00:00Z",
                "CNSHA", "2026-10-03T00:00:00Z", "USLAX", "2026-10-11T00:00:00Z");

        private final Voyage lateVoyage = 航海("V-LATE", Set.of(RoutingCargoType.GENERAL),
                "JPOSA", "2026-10-01T00:00:00Z", "USLAX", "2026-12-01T00:00:00Z");

        private List<String> 順番(List<ProposedRoute> routes) {
            return routes.stream().map(r -> r.voyageNumber().value()).toList();
        }

        /** 受入基準: 期限内の候補が先に来る。**期限を満たすことが最優先である。** */
        @Test
        void 期限を満たす候補が先に来る() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-20"),
                    List.of(lateVoyage, directVoyage));

            assertThat(順番(routes)).containsExactly("V-DIRECT", "V-LATE");
        }

        /**
         * 受入基準: <strong>直行便がある場合、最優先候補として提示される</strong>。
         *
         * <p>所要日数では経由便のほうが短くても、直行が先に来る。
         * 乗り継ぎは遅延の影響を受けやすく、日数だけでは比べられない。
         */
        @Test
        void 直行便は所要日数が長くても先に来る() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-20"),
                    List.of(fasterViaVoyage, directVoyage));

            assertThat(順番(routes)).containsExactly("V-DIRECT", "V-FAST");
        }

        /** 同じ条件なら所要日数の短い順。 */
        @Test
        void 直行同士なら所要日数の短い順() {
            var slowDirectVoyage = 航海("V-SLOW", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T00:00:00Z", "USLAX", "2026-10-25T00:00:00Z");

            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-31"),
                    List.of(slowDirectVoyage, directVoyage));

            assertThat(順番(routes)).containsExactly("V-DIRECT", "V-SLOW");
        }

        /** 表示順が候補に振られる。**画面が並べ替えの規則を持たないようにする。** */
        @Test
        void 表示順が候補に振られる() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-20"),
                    List.of(lateVoyage, directVoyage));

            assertThat(routes).extracting(ProposedRoute::priority).containsExactly(1, 2);
        }
    }

    @Nested
    @DisplayName("概算費用（ADR-008）")
    class 概算費用 {

        /** 重量と所要日数から概算する。**運賃表を持たないことを認めた上での目安である。** */
        @Test
        void 重量と日数で概算する() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-31"),
                    List.of(大阪発ロサンゼルス行き直行便()));

            // 1000kg = 1t、13 日、単価 100 → 1300
            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.estimatedCost().value())
                            .isEqualByComparingTo(new BigDecimal("1300")));
        }

        /** 危険物には割増が付く。**同じ船倉を使えないため、実務でも高い。** */
        @Test
        void 危険物には割増が付く() {
            var voyage = 航海("V040",
                    Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-10-14T06:00:00Z");
            var criteria = RoutingCriteria.of(
                    Location.of("JPOSA"), Location.of("USLAX"),
                    LocalDate.parse("2026-10-31"), RoutingCargoType.HAZARDOUS,
                    RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);

            var routes = search.search(criteria, List.of(voyage));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.estimatedCost().value())
                            .isEqualByComparingTo(new BigDecimal("1950")));
        }

        /** 通貨を持つ。**単位の無い金額は金額ではない。** */
        @Test
        void 通貨を持つ() {
            var routes = search.search(条件("JPOSA", "USLAX", "2026-10-31"),
                    List.of(大阪発ロサンゼルス行き直行便()));

            assertThat(routes).singleElement()
                    .satisfies(r -> assertThat(r.estimatedCost().currency()).isEqualTo("JPY"));
        }
    }

    @Nested
    @DisplayName("経路提案（集約）")
    class 経路提案 {

        private final RoutingBookingId bookingId = new RoutingBookingId(UUID.randomUUID());

        /** 候補が 0 件でも提案は成立する。**候補ゼロは異常ではなく状態である。** */
        @Test
        void 候補ゼロの提案を保持できる() {
            var proposal = BookingRouteProposal.propose(
                    bookingId, 条件("JPOSA", "USLAX", "2026-10-20"), List.of());

            assertThat(proposal.hasNoCandidate()).isTrue();
            assertThat(proposal.candidateCount()).isZero();
        }

        /** 再算出のたびに算出回数が増える。**何回試したかが、条件を緩める判断の材料になる。** */
        @Test
        void 再算出すると算出回数が増える() {
            var criteria = 条件("JPOSA", "USLAX", "2026-10-20");
            var proposal = BookingRouteProposal.propose(bookingId, criteria, List.of());

            var recalculated = proposal.recalculate(
                    criteria, search.search(criteria, List.of(大阪発ロサンゼルス行き直行便())));

            assertThat(proposal.calculationCount()).isEqualTo(1);
            assertThat(recalculated.calculationCount()).isEqualTo(2);
            assertThat(recalculated.candidateCount()).isEqualTo(1);
        }

        /** 期限内に着ける候補が 1 つも無いことを区別できる（受入基準）。 */
        @Test
        void 期限内に到達できる候補が無いことが分かる() {
            var criteria = 条件("JPOSA", "USLAX", "2026-10-14");
            var voyage = 航海("V050", Set.of(RoutingCargoType.GENERAL),
                    "JPOSA", "2026-10-01T10:00:00Z", "USLAX", "2026-11-30T06:00:00Z");

            var proposal = BookingRouteProposal.propose(
                    bookingId, criteria, search.search(criteria, List.of(voyage)));

            assertThat(proposal.hasNoCandidate()).isFalse();
            assertThat(proposal.hasNoDeadlineSatisfyingCandidate()).isTrue();
        }

        /** 当初の期限を保持する（US10 で延長したときの差分を荷主に伝えるため）。 */
        @Test
        void 当初の期限を保持する() {
            var criteria = 条件("JPOSA", "USLAX", "2026-10-14");
            var proposal = BookingRouteProposal.propose(bookingId, criteria, List.of());

            var relaxed = proposal.recalculate(criteria.withDeadline(
                    LocalDate.parse("2026-10-21")), List.of());

            assertThat(relaxed.criteria().arrivalDeadline())
                    .isEqualTo(LocalDate.parse("2026-10-21"));
            assertThat(relaxed.criteria().originalArrivalDeadline())
                    .isEqualTo(LocalDate.parse("2026-10-14"));
        }

        /** 予約 ID の無い提案は成立しない。 */
        @Test
        void 予約IDが無い提案を拒否する() {
            assertThatThrownBy(() -> BookingRouteProposal.propose(
                    null, 条件("JPOSA", "USLAX", "2026-10-20"), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
