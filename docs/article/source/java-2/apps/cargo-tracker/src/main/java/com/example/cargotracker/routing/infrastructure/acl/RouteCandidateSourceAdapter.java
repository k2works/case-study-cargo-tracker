package com.example.cargotracker.routing.infrastructure.acl;

import com.example.cargotracker.estimation.application.internal.outboundservices.acl
        .RouteCandidateSource;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 見積へルート候補を渡すアダプタ（ADR-023）。
 *
 * <p><strong>アダプタは提供側に置く。</strong> ポートを所有するのは Estimation で
 * あり、その実装を持つのは探索を持つ Routing である（`RoutableBookingsAdapter` と
 * 同じ形）。
 *
 * <p><strong>ここが唯一の越境点である。</strong> Routing の型（{@code ProposedRoute} /
 * {@code RoutingCargoType}）は本クラスの外へ出さない。
 */
@Service
public class RouteCandidateSourceAdapter implements RouteCandidateSource {

    private final VoyageRepository voyageRepository;
    private final RouteSearchService routeSearchService;

    public RouteCandidateSourceAdapter(
            VoyageRepository voyageRepository, RouteSearchService routeSearchService) {
        this.voyageRepository = voyageRepository;
        this.routeSearchService = routeSearchService;
    }

    @Override
    public Candidates findCandidates(Query query) {
        Location origin = Location.of(query.originUnlocode());
        Location destination = Location.of(query.destinationUnlocode());

        List<Voyage> voyages = voyageRepository.findConnecting(origin, destination);
        if (voyages.isEmpty()) {
            // **便が無いことと、期限に間に合わないことは別である**（ADR-023）。
            // まとめて「候補がありません」と返すと、営業担当者は次に何をすべきか
            // 判断できない
            return new Candidates(List.of(), false);
        }

        RoutingCriteria criteria = new RoutingCriteria(
                origin,
                destination,
                query.arrivalDeadline(),
                query.arrivalDeadline(),
                RoutingCargoType.valueOf(query.cargoType()),
                new RoutingWeight(query.weightKg()),
                RoutingCriteria.DEFAULT_MAX_TRANSIT_COUNT);

        // **期限を満たす候補だけを見積に載せる。** 見積は「間に合う経路の概算」で
        // あり、間に合わない経路を並べても荷主の判断材料にならない
        List<Candidate> candidates = routeSearchService.search(criteria, voyages).stream()
                .filter(ProposedRoute::deadlineSatisfied)
                .map(RouteCandidateSourceAdapter::toCandidate)
                .toList();
        return new Candidates(candidates, true);
    }

    private static Candidate toCandidate(ProposedRoute route) {
        return new Candidate(
                route.voyageNumber().value(),
                route.isDirect() ? null : route.transitPorts().getFirst().unlocode(),
                route.transitDays(),
                route.estimatedCost().value(),
                route.estimatedCost().currency());
    }
}
