package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.aggregates.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.entities.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.FreightEstimator;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageNumber;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
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
 * 経路候補の並べ方・費用・提案の保持（US08）。
 *
 * <p>探索そのもの（どの航海が候補になるか）は {@code RouteSearchTest} が担う。
 * <strong>「見つける」と「どう見せるか」は別の関心事</strong>であり、
 * 片方を直したときに落ちるテストが分かれているほうが原因を追いやすい。
 */
@DisplayName("経路候補の並べ方と提案（US08）")
class RouteProposalDomainTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final FreightEstimator estimator =
            new FreightEstimator(new BigDecimal("100"), new BigDecimal("1.5"));

    private final RouteSearchService search = new RouteSearchService(estimator, JST);

    private Voyage 航海(String number, Set<RoutingCargoType> types, String... legs) {
        List<CarrierMovement> movements = new java.util.ArrayList<>();
        for (int i = 0; i < legs.length; i += 4) {
            movements.add(CarrierMovement.of(
                    Location.of(legs[i]), Location.of(legs[i + 2]),
                    Instant.parse(legs[i + 1]), Instant.parse(legs[i + 3])));
        }
        return Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"),
                new CarrierName("日本海運"), Schedule.of(movements), types,
                RoutingWeight.ofKilograms(new BigDecimal("100000"))));
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
     * 候補が無ければ並べ替えるものも無い。
     *
     * <p><strong>空を特別扱いしない。</strong> 0 件のときだけ落ちる並べ替えは、
     * 候補ゼロが日常的に起きるこの業務では致命的である。
     */
    @Test
    void 候補が無くても推奨順の付与は成り立つ() {
        var routes = search.search(条件("JPOSA", "USLAX", "2026-10-20"), List.of());

        assertThat(routes).isEmpty();
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
