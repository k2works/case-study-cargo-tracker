package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutableBookings;
import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.ProposedRoute;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 経路割り当て画面の読み取り（US08。CQRS のクエリ側）。 */
@Service
public class RouteProposalQueryService {

    private final RoutableBookings routableBookings;
    private final BookingRouteProposalRepository proposalRepository;

    public RouteProposalQueryService(
            RoutableBookings routableBookings,
            BookingRouteProposalRepository proposalRepository) {
        this.routableBookings = routableBookings;
        this.proposalRepository = proposalRepository;
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
                booking.get().shipperName(),
                booking.get().originUnlocode(),
                booking.get().destinationUnlocode(),
                booking.get().arrivalDeadline(),
                // 表示名は Routing のことばに直してから出す
                RoutingCargoType.valueOf(booking.get().cargoType()).displayName(),
                booking.get().weightKilograms(),
                proposal.isPresent(),
                proposal.map(RouteProposalQueryService::toCandidates).orElseGet(List::of)));
    }

    private static List<RouteProposalView.Candidate> toCandidates(BookingRouteProposal proposal) {
        return proposal.candidates().stream()
                .map(RouteProposalQueryService::toCandidate)
                .toList();
    }

    private static RouteProposalView.Candidate toCandidate(ProposedRoute route) {
        return new RouteProposalView.Candidate(
                route.priority(),
                route.voyageNumber().value(),
                // 直行かどうかは経路設計で最初に見る情報である
                route.isDirect()
                        ? "直行"
                        : route.transitPorts().stream()
                                .map(Location::unlocode)
                                .collect(Collectors.joining(" → ")),
                route.departureTime(),
                route.arrivalTime(),
                route.transitDays(),
                route.estimatedCost().value(),
                route.estimatedCost().currency(),
                route.deadlineSatisfied(),
                route.selectable(),
                route.unselectableReason());
    }
}
