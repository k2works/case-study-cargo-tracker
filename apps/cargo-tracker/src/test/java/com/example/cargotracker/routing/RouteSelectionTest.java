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
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路候補の選択（US09）。
 *
 * <p><strong>選べない候補は選べない。</strong> 一覧に残すこと（IT4）と、
 * 選択を通すことは別である。画面で無効にするだけでは、
 * URL や再送で通ってしまう。
 */
@DisplayName("経路候補の選択（US09）")
class RouteSelectionTest {

    private final RoutingBookingId bookingId = new RoutingBookingId(UUID.randomUUID());

    private RoutingCriteria 条件(RoutingCargoType cargoType) {
        return RoutingCriteria.of(
                Location.of("JPOSA"), Location.of("USLAX"),
                LocalDate.parse("2026-10-20"), cargoType,
                RoutingWeight.ofKilograms(new BigDecimal("1000")), 2);
    }

    private ProposedRoute 候補(String voyageNumber, ProposedRoute.Handling handling) {
        return ProposedRoute.reconstruct(
                new VoyageNumber(voyageNumber),
                new ProposedRoute.Path(List.of(), new ProposedRoute.LegRange(0, 0)),
                new ProposedRoute.Timing(
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-14T06:00:00Z"), 13),
                Money.yen(new BigDecimal("1300")),
                handling,
                true,
                1);
    }

    /**
     * 選択を実行する。
     *
     * <p>選択は<strong>新しい提案を返す</strong>ため、捨てずに受け取る。
     * 捨てると「選んだのに反映されない」書き方を、テストが手本にしてしまう。
     */
    private BookingRouteProposal 選択する(BookingRouteProposal proposal, String voyageNumber) {
        return proposal.select(new VoyageNumber(voyageNumber));
    }

    private ProposedRoute 選べる候補(String voyageNumber) {
        return 候補(voyageNumber,
                new ProposedRoute.Handling(RoutingCargoType.GENERAL, true, true, true));
    }

    /** 受入基準: 最適な経路候補を 1 件選択できる。 */
    @Test
    void 候補を1件選択できる() {
        var proposal = BookingRouteProposal.propose(bookingId, 条件(RoutingCargoType.GENERAL),
                List.of(選べる候補("V001"), 選べる候補("V002")));

        var selected = proposal.select(new VoyageNumber("V002"));

        assertThat(selected.isSelected()).isTrue();
        assertThat(selected.selectedRoute().voyageNumber().value()).isEqualTo("V002");
    }

    /** 受入基準: 選択後、経路状態が確定になる。**選ぶまでは確定していない。** */
    @Test
    void 選択するまでは確定していない() {
        var proposal = BookingRouteProposal.propose(bookingId, 条件(RoutingCargoType.GENERAL),
                List.of(選べる候補("V001")));

        assertThat(proposal.isSelected()).isFalse();
        assertThat(proposal.selectedRoute()).isNull();
    }

    /**
     * <strong>運べない便は選べない。</strong>
     *
     * <p>一覧に残す（IT4）のは「なぜ出てこないのか」を確認できるようにするためであり、
     * 選べるようにするためではない。
     */
    @Test
    void 運べない便は選べない() {
        var proposal = BookingRouteProposal.propose(bookingId,
                条件(RoutingCargoType.HAZARDOUS),
                List.of(候補("V-NO-HAZ",
                        new ProposedRoute.Handling(
                                RoutingCargoType.HAZARDOUS, false, false, true))));

        assertThatThrownBy(() -> 選択する(proposal, "V-NO-HAZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("危険物");
    }

    /**
     * <strong>満船の便は選べない</strong>（IT4 で先送りにした判定）。
     *
     * <p>選べてしまうと、積めない貨物に経路を確定したことになる。
     */
    @Test
    void 満船の便は選べない() {
        var proposal = BookingRouteProposal.propose(bookingId, 条件(RoutingCargoType.GENERAL),
                List.of(候補("V-FULL",
                        new ProposedRoute.Handling(
                                RoutingCargoType.GENERAL, true, true, false))));

        assertThatThrownBy(() -> 選択する(proposal, "V-FULL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空き");
    }

    /** 候補に無い航海番号は選べない。**URL を直接編集しただけで通らない。** */
    @Test
    void 候補に無い航海番号は選べない() {
        var proposal = BookingRouteProposal.propose(bookingId, 条件(RoutingCargoType.GENERAL),
                List.of(選べる候補("V001")));

        assertThatThrownBy(() -> 選択する(proposal, "V-NOT-LISTED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 候補ゼロの提案からは選べない。 */
    @Test
    void 候補ゼロの提案からは選べない() {
        var proposal = BookingRouteProposal.propose(bookingId,
                条件(RoutingCargoType.GENERAL), List.of());

        assertThatThrownBy(() -> 選択する(proposal, "V001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 再算出すると選択は解除される。**古い候補への選択が残ると、消えた便を指す。** */
    @Test
    void 再算出すると選択は解除される() {
        var selected = BookingRouteProposal
                .propose(bookingId, 条件(RoutingCargoType.GENERAL), List.of(選べる候補("V001")))
                .select(new VoyageNumber("V001"));

        var recalculated = selected.recalculate(
                条件(RoutingCargoType.GENERAL), List.of(選べる候補("V002")));

        assertThat(recalculated.isSelected()).isFalse();
    }

    /** 選び直せる。**確定は US09 の操作であり、間違えたら選び直せる必要がある。** */
    @Test
    void 選び直せる() {
        var proposal = BookingRouteProposal
                .propose(bookingId, 条件(RoutingCargoType.GENERAL),
                        List.of(選べる候補("V001"), 選べる候補("V002")))
                .select(new VoyageNumber("V001"));

        var reselected = proposal.select(new VoyageNumber("V002"));

        assertThat(reselected.selectedRoute().voyageNumber().value()).isEqualTo("V002");
    }
}
