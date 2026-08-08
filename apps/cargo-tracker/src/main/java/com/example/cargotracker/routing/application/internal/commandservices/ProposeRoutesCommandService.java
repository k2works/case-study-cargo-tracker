package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutableBookings;
import com.example.cargotracker.routing.domain.model.BookingRouteProposal;
import com.example.cargotracker.routing.domain.model.RelaxationRequest;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import com.example.cargotracker.routing.domain.model.RoutingBookingId;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.Voyage;
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
     *
     * <p><strong>緩和は「いま保存されている条件」に対して積む。</strong> 予約から作り直すと、
     * 前回延ばした 3 日が消えて毎回同じ場所から数え直すことになる。
     * <strong>当初の期限は {@code RoutingCriteria} が持ち続ける</strong>ため、
     * 何日延ばしたかは積んでも分かる。
     */
    @Transactional
    public Optional<BookingRouteProposal> propose(
            RoutingBookingId bookingId, RelaxationRequest relaxation, String actor) {
        var booking = routableBookings.find(bookingId.value());
        if (booking.isEmpty()) {
            return Optional.empty();
        }

        Optional<BookingRouteProposal> existing = proposalRepository.findByBookingId(bookingId);

        // ACL は素の値を返す。**Routing のことばへの翻訳はここで行う**
        //
        // **提案があるなら、緩めていなくても保存済みの条件を土台にする。**
        // 毎回予約から作り直すと、候補を出し直すだけの再算出で延ばした日数が消え、
        // 荷主に伝えるべき差分（US12）が気づかないうちに失われる
        RoutingCriteria base = existing
                .map(BookingRouteProposal::criteria)
                .orElseGet(() -> RoutingCriteria.of(
                        Location.of(booking.get().originUnlocode()),
                        Location.of(booking.get().destinationUnlocode()),
                        booking.get().arrivalDeadline(),
                        RoutingCargoType.valueOf(booking.get().cargoType()),
                        RoutingWeight.ofKilograms(booking.get().weightKilograms()),
                        DEFAULT_MAX_TRANSIT_COUNT));
        RoutingCriteria criteria;
        try {
            criteria = relaxation.applyTo(base);
        } catch (IllegalArgumentException e) {
            // 累積の上限を超えた。**切り詰めて探し直さない**（要求と違う条件の結果を
            // 要求どおりの結果として返さないため）。呼び出し元がそのまま画面に出す
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        var voyages = voyageRepository.findConnecting(criteria.origin(), criteria.destination());
        // **割当済みの重量を渡す。** 渡さないと、何件割り当てても「空きあり」を返し続ける
        var assignedWeights = voyageRepository.findAssignedWeights(
                voyages.stream().map(Voyage::voyageNumber).toList());
        var candidates = routeSearchService.search(criteria, voyages, assignedWeights);

        BookingRouteProposal proposal = existing
                .map(current -> current.recalculate(criteria, candidates))
                .orElseGet(() -> BookingRouteProposal.propose(bookingId, criteria, candidates));
        proposalRepository.save(proposal);

        if (AUDIT.isInfoEnabled()) {
            AUDIT.info("経路候補算出 bookingId={} origin={} destination={} 候補={} 回={}"
                            + " 期限={} 当初期限={} 経由上限={} actor={}",
                    bookingId.value(),
                    criteria.origin().unlocode(),
                    criteria.destination().unlocode(),
                    proposal.candidateCount(),
                    proposal.calculationCount(),
                    criteria.arrivalDeadline(),
                    criteria.originalArrivalDeadline(),
                    criteria.maxTransitCount(),
                    AuditValue.sanitize(actor));
        }

        return Optional.of(proposal);
    }
}
