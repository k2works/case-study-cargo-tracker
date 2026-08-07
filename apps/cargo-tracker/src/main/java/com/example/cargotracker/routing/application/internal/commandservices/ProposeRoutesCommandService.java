package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutableBookings;
import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.logging.AuditValue;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 経路候補の算出（US08）。 */
@Service
public class ProposeRoutesCommandService {

    /** 業務操作ログ（{@code non_functional.md} §4.4）。 */
    private static final Logger AUDIT = LoggerFactory.getLogger("audit.routing");

    /** 経由回数の既定の上限（{@code data-model.md} の {@code max_transit_count}）。 */
    private static final int DEFAULT_MAX_TRANSIT_COUNT = 2;

    private final RoutableBookings routableBookings;
    private final VoyageRepository voyageRepository;
    private final BookingRouteProposalRepository proposalRepository;
    private final RouteSearchService routeSearchService;

    public ProposeRoutesCommandService(
            RoutableBookings routableBookings,
            VoyageRepository voyageRepository,
            BookingRouteProposalRepository proposalRepository,
            RouteSearchService routeSearchService) {
        this.routableBookings = routableBookings;
        this.voyageRepository = voyageRepository;
        this.proposalRepository = proposalRepository;
        this.routeSearchService = routeSearchService;
    }

    /**
     * 予約に対する経路候補を算出して保存する。
     *
     * <p>すでに提案があれば<strong>算出し直して入れ替える</strong>。
     * 候補が 0 件でも提案は保存する。<strong>候補ゼロは異常ではなく状態</strong>であり、
     * 経路割り当て待ち一覧に出す必要がある。
     */
    @Transactional
    public Optional<BookingRouteProposal> propose(RoutingBookingId bookingId, String actor) {
        var booking = routableBookings.find(bookingId.value());
        if (booking.isEmpty()) {
            return Optional.empty();
        }

        // ACL は素の値を返す。**Routing のことばへの翻訳はここで行う**
        RoutingCriteria criteria = RoutingCriteria.of(
                Location.of(booking.get().originUnlocode()),
                Location.of(booking.get().destinationUnlocode()),
                booking.get().arrivalDeadline(),
                RoutingCargoType.valueOf(booking.get().cargoType()),
                RoutingWeight.ofKilograms(booking.get().weightKilograms()),
                DEFAULT_MAX_TRANSIT_COUNT);

        var candidates = routeSearchService.search(criteria,
                voyageRepository.findConnecting(criteria.origin(), criteria.destination()));

        BookingRouteProposal proposal = proposalRepository.findByBookingId(bookingId)
                .map(existing -> existing.recalculate(criteria, candidates))
                .orElseGet(() -> BookingRouteProposal.propose(bookingId, criteria, candidates));
        proposalRepository.save(proposal);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("経路候補算出 bookingId={} origin={} destination={} 候補={} 回={} actor={}",
                    bookingId.value(),
                    criteria.origin().unlocode(),
                    criteria.destination().unlocode(),
                    proposal.candidateCount(),
                    proposal.calculationCount(),
                    AuditValue.sanitize(actor));
        }

        return Optional.of(proposal);
    }
}
