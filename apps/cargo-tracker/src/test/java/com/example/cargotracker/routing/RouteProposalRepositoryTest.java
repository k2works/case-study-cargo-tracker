package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.Money;
import com.example.cargotracker.routing.domain.model.ProposedRoute;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 経路提案の永続化（US08）。
 *
 * <p>SQL の正しさは実 PostgreSQL で確かめる（ADR-003）。
 */
class RouteProposalRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private BookingRouteProposalRepository repository;

    private RoutingCriteria 条件() {
        return RoutingCriteria.of(
                Location.of("JPOSA"), Location.of("USLAX"),
                LocalDate.parse("2026-10-20"), RoutingCargoType.GENERAL,
                RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
    }

    private ProposedRoute 候補(String voyageNumber, List<String> transitPorts, int priority) {
        return ProposedRoute.reconstruct(
                new VoyageNumber(voyageNumber),
                transitPorts.stream().map(Location::of).toList(),
                new ProposedRoute.Timing(
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-14T06:00:00Z"), 13),
                Money.yen(new BigDecimal("1300")),
                new ProposedRoute.Handling(RoutingCargoType.GENERAL, true, false, true),
                true,
                priority);
    }

    /** 保存した提案と候補を読み戻せる。 */
    @Test
    void 提案と候補を往復できる() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        repository.save(BookingRouteProposal.propose(bookingId, 条件(),
                List.of(候補("V001", List.of(), 1), 候補("V002", List.of("CNSHA"), 2))));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.candidateCount()).isEqualTo(2);
        assertThat(loaded.criteria().origin().unlocode()).isEqualTo("JPOSA");
        assertThat(loaded.criteria().arrivalDeadline()).isEqualTo(LocalDate.parse("2026-10-20"));
    }

    /** <strong>表示順どおりに読み戻す。</strong> 順序が崩れると推奨順が意味を失う。 */
    @Test
    void 候補は表示順で読み戻される() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        repository.save(BookingRouteProposal.propose(bookingId, 条件(),
                List.of(候補("V-SECOND", List.of(), 2), 候補("V-FIRST", List.of(), 1))));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.candidates())
                .extracting(r -> r.voyageNumber().value())
                .containsExactly("V-FIRST", "V-SECOND");
    }

    /** 経由港が読み戻せる。直行は空で保存する。 */
    @Test
    void 経由港が往復する() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        repository.save(BookingRouteProposal.propose(bookingId, 条件(),
                List.of(候補("V010", List.of("CNSHA", "HKHKG"), 1), 候補("V011", List.of(), 2))));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.candidates().get(0).transitPorts())
                .extracting(Location::unlocode).containsExactly("CNSHA", "HKHKG");
        assertThat(loaded.candidates().get(1).transitPorts()).isEmpty();
    }

    /**
     * <strong>再算出は候補を丸ごと入れ替える</strong>（ビジネスルール 5）。
     *
     * <p>前回の候補が残ると、どの候補がどの条件で出たものか分からなくなる。
     */
    @Test
    void 再算出すると候補が入れ替わる() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        var proposal = BookingRouteProposal.propose(bookingId, 条件(),
                List.of(候補("V-OLD", List.of(), 1)));
        repository.save(proposal);

        repository.save(proposal.recalculate(条件(), List.of(候補("V-NEW", List.of(), 1))));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();
        assertThat(loaded.candidates())
                .extracting(r -> r.voyageNumber().value())
                .containsExactly("V-NEW");
        assertThat(loaded.calculationCount()).isEqualTo(2);
    }

    /** 候補ゼロも保存できる。**状態として残らないと一覧に出せない。** */
    @Test
    void 候補ゼロを保存できる() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        repository.save(BookingRouteProposal.propose(bookingId, 条件(), List.of()));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.hasNoCandidate()).isTrue();
    }

    /** 当初の期限を保持したまま往復する（US10 の差分を荷主に伝えるため）。 */
    @Test
    void 当初の期限が往復する() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        var proposal = BookingRouteProposal.propose(bookingId, 条件(), List.of());
        repository.save(proposal);
        repository.save(proposal.recalculate(
                条件().withDeadline(LocalDate.parse("2026-10-27")), List.of()));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.criteria().arrivalDeadline()).isEqualTo(LocalDate.parse("2026-10-27"));
        assertThat(loaded.criteria().originalArrivalDeadline())
                .isEqualTo(LocalDate.parse("2026-10-20"));
    }

    /**
     * <strong>選べない候補は、読み戻しても選べない。</strong>
     *
     * <p>選択可否は「この貨物は何か」と「この便は何を運べるか」の両方で決まる。
     * 読み戻しで前者を落とすと、<strong>危険物の予約に危険物を扱えない便が
     * 選べる候補として並ぶ</strong>。保存のときだけ効く安全装置は、
     * 安全装置ではない。
     */
    @Test
    void 危険物の候補は読み戻しても選べない() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        var criteria = RoutingCriteria.of(
                Location.of("JPOSA"), Location.of("USLAX"),
                LocalDate.parse("2026-10-20"), RoutingCargoType.HAZARDOUS,
                RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
        var candidate = ProposedRoute.reconstruct(
                new VoyageNumber("V-NO-HAZ"),
                List.of(),
                new ProposedRoute.Timing(
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-14T06:00:00Z"), 13),
                Money.yen(new BigDecimal("1950")),
                new ProposedRoute.Handling(RoutingCargoType.HAZARDOUS, false, false, true),
                true,
                1);
        repository.save(BookingRouteProposal.propose(bookingId, criteria, List.of(candidate)));

        var loaded = repository.findByBookingId(bookingId).orElseThrow();

        assertThat(loaded.candidates()).singleElement().satisfies(r -> {
            assertThat(r.selectable()).isFalse();
            assertThat(r.unselectableReason()).contains("危険物");
        });
    }

    /**
     * <strong>同時に算出したとき、後の保存が黙って前を上書きしない。</strong>
     *
     * <p>{@code booking_route_proposal} は楽観的ロックの列を持つ（判断 8）。
     * <strong>列があるのに WHERE 句で見ていなければ、持っていないのと同じ</strong>で
     * あり、次に読む人は守られていると誤解する。
     */
    @Test
    void 同時に算出すると後の保存が拒否される() {
        var bookingId = new RoutingBookingId(UUID.randomUUID());
        repository.save(BookingRouteProposal.propose(bookingId, 条件(),
                List.of(候補("V-FIRST", List.of(), 1))));

        // 2 人が同じ提案を読み、それぞれ再算出する
        var loadedByA = repository.findByBookingId(bookingId).orElseThrow();
        var loadedByB = repository.findByBookingId(bookingId).orElseThrow();

        repository.save(loadedByA.recalculate(条件(), List.of(候補("V-BY-A", List.of(), 1))));

        assertThatThrownBy(() -> repository.save(
                loadedByB.recalculate(条件(), List.of(候補("V-BY-B", List.of(), 1)))))
                .isInstanceOf(ConcurrentModificationException.class);

        // 先に保存した側の結果が残る
        assertThat(repository.findByBookingId(bookingId).orElseThrow().candidates())
                .extracting(r -> r.voyageNumber().value())
                .containsExactly("V-BY-A");
    }

    /** 提案の無い予約は空を返す。 */
    @Test
    void 提案の無い予約は空を返す() {
        assertThat(repository.findByBookingId(new RoutingBookingId(UUID.randomUUID())))
                .isEmpty();
    }
}
