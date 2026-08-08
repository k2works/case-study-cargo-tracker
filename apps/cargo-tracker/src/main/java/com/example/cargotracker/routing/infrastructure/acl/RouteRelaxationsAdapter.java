package com.example.cargotracker.routing.infrastructure.acl;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.RouteRelaxations;
import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link RouteRelaxations} の実装（ACL のアダプタ）。
 *
 * <p>期限を緩めたかどうかの判断は <strong>{@code RoutingCriteria} が持つ</strong>。
 * ここでするのは、素の値への翻訳だけである。
 */
@Component
public class RouteRelaxationsAdapter implements RouteRelaxations {

    private final BookingRouteProposalRepository proposalRepository;

    public RouteRelaxationsAdapter(BookingRouteProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }

    @Override
    public Optional<Relaxation> find(String bookingId) {
        RoutingBookingId id;
        try {
            id = RoutingBookingId.of(bookingId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return proposalRepository.findByBookingId(id)
                .map(BookingRouteProposal::criteria)
                .filter(RoutingCriteria::isDeadlineRelaxed)
                .map(criteria -> new Relaxation(
                        criteria.originalArrivalDeadline(),
                        ChronoUnit.DAYS.between(
                                criteria.originalArrivalDeadline(), criteria.arrivalDeadline())));
    }
}
