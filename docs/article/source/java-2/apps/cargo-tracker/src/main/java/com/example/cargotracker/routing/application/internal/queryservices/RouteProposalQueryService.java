package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutableBookings;
import com.example.cargotracker.routing.domain.model.aggregates.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 経路割り当て画面の読み取り（US08。CQRS のクエリ側）。 */
@Service
public class RouteProposalQueryService {

    private final RoutableBookings routableBookings;
    private final BookingRouteProposalRepository proposalRepository;

    /** **業務のタイムゾーンで日付を決める。** UTC で判断すると期限の境界がずれる。 */
    private final Clock clock;

    public RouteProposalQueryService(
            RoutableBookings routableBookings,
            BookingRouteProposalRepository proposalRepository,
            Clock clock) {
        this.routableBookings = routableBookings;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    /**
     * 画面 1 つ分を読む。
     *
     * <p>まだ算出していない予約も<strong>開ける</strong>。算出していないことと、
     * 算出して 0 件だったことは別の状態である。
     *
     * @return 経路割り当ての対象でなければ空
     */
    public Optional<RouteProposalView> find(RoutingBookingId bookingId) {
        var booking = routableBookings.find(bookingId.value());
        if (booking.isEmpty()) {
            return Optional.empty();
        }
        Optional<BookingRouteProposal> proposal = proposalRepository.findByBookingId(bookingId);

        return Optional.of(new RouteProposalView(
                bookingId.value().toString(),
                new RouteProposalView.CargoSummary(
                        booking.get().shipperName(),
                        booking.get().originUnlocode(),
                        booking.get().destinationUnlocode(),
                        booking.get().arrivalDeadline(),
                        // 表示名は Routing のことばに直してから出す
                        RoutingCargoType.valueOf(booking.get().cargoType()).displayName(),
                        booking.get().weightKilograms(),
                        booking.get().misroutedFrom()),
                new RouteProposalView.SearchCriteria(
                        // **探索に使った条件を出す。** 予約の期限をそのまま出すと、
                        // 延ばして探した結果を「元の期限で探した結果」として読ませてしまう
                        proposal.map(p -> p.criteria().arrivalDeadline())
                                .orElseGet(() -> booking.get().arrivalDeadline()),
                        proposal.map(p -> p.criteria().maxTransitCount())
                                .orElse(RoutingCriteria.DEFAULT_MAX_TRANSIT_COUNT),
                        proposal.map(p -> p.criteria().extraDays()).orElse(0L)),
                new RouteProposalView.Result(
                        proposal.isPresent(),
                        proposal.map(p -> toCandidates(
                                        p, booking.get().arrivalDeadline(), clock.getZone()))
                                .orElseGet(List::of))));
    }

    private static List<RouteProposalView.Candidate> toCandidates(
            BookingRouteProposal proposal, LocalDate originalDeadline, ZoneId zone) {
        return proposal.candidates().stream()
                .map(route -> toCandidate(route, originalDeadline, zone))
                .toList();
    }

    private static RouteProposalView.Candidate toCandidate(
            ProposedRoute route, LocalDate originalDeadline, ZoneId zone) {
        return new RouteProposalView.Candidate(
                route.priority(),
                route.voyageNumber().value(),
                // 直行かどうかは経路設計で最初に見る情報である
                route.isDirect()
                        ? "直行"
                        : route.transitPorts().stream()
                                .map(Location::unlocode)
                                .collect(Collectors.joining(" → ")),
                new RouteProposalView.Candidate.Schedule(
                        route.departureTime(), route.arrivalTime(), route.transitDays()),
                new RouteProposalView.Candidate.Cost(
                        route.estimatedCost().value(), route.estimatedCost().currency()),
                new RouteProposalView.Candidate.Availability(
                        route.capacityAvailable(),
                        route.deadlineSatisfied(),
                        route.selectable(),
                        route.unselectableReason(),
                        daysOverDeadline(route, originalDeadline, zone)));
    }

    /**
     * 当初の希望期限を何日超えるか（US28）。
     *
     * <p><strong>探索に使った期限ではなく、予約の期限と比べる。</strong> 期限を延ばして
     * 探した場合、延ばした期限に「間に合っている」ことは荷主の関心ではない。
     * 荷主が知りたいのは<strong>当初の約束から何日ずれたか</strong>である。
     *
     * <p><strong>日付単位で比べる</strong>（{@code domain-model.md} ルール 2-1）。
     * 期限は日付であり、到着は時刻を持つ。素朴に比べると期限当日の到着が超過になる。
     */
    private static long daysOverDeadline(
            ProposedRoute route, LocalDate originalDeadline, ZoneId zone) {
        if (originalDeadline == null) {
            return 0L;
        }
        LocalDate arrival = route.arrivalTime().atZone(zone).toLocalDate();
        return Math.max(0L, ChronoUnit.DAYS.between(originalDeadline, arrival));
    }
}
