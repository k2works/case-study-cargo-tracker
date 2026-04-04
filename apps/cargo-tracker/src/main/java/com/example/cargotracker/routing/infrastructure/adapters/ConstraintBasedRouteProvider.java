package com.example.cargotracker.routing.infrastructure.adapters;

import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DB 内の航海データをもとにルート候補を算出する {@link RouteProviderPort} 実装。
 *
 * <p>{@code product} プロファイルで有効になる。
 * {@link VoyageQueryPort} で検索した全航海を {@link RouteCandidate} へ変換して返す。
 * 制約フィルタリングは呼び出し元の
 * {@link com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService} が担う。
 */
@Component
@Profile("product")
public class ConstraintBasedRouteProvider implements RouteProviderPort {

    /** 重量あたりの基本運賃（JPY/kg）。 */
    private static final BigDecimal BASE_RATE_PER_KG = new BigDecimal("50");
    /** 所要日数あたりの運賃（JPY/日）。 */
    private static final BigDecimal DAY_RATE = new BigDecimal("1000");

    private final VoyageQueryPort voyageQueryPort;

    public ConstraintBasedRouteProvider(VoyageQueryPort voyageQueryPort) {
        this.voyageQueryPort = voyageQueryPort;
    }

    @Override
    public List<RouteCandidate> findRoutes(RouteSearchQuery query) {
        return voyageQueryPort.searchVoyages(query.originLocode(), query.destinationLocode())
            .stream()
            .map(v -> toRouteCandidate(v, query))
            .toList();
    }

    private RouteCandidate toRouteCandidate(Voyage voyage, RouteSearchQuery query) {
        List<String> viaLocodes = buildViaLocodes(voyage);
        int rawTransitDays = voyage.legs().stream().mapToInt(VoyageLeg::transitDays).sum();
        int transitDays = Math.max(rawTransitDays, 1);
        BigDecimal estimatedPrice = BASE_RATE_PER_KG.multiply(query.weightKg())
            .add(DAY_RATE.multiply(BigDecimal.valueOf(transitDays)));

        return new RouteCandidate(
            voyage.voyageNumber(),
            viaLocodes,
            transitDays,
            estimatedPrice,
            voyage.latestArrivalDate(),
            voyage.firstDepartureDate(),
            voyage.supportedCargoTypes()
        );
    }

    private List<String> buildViaLocodes(Voyage voyage) {
        List<String> locodes = new ArrayList<>();
        locodes.add(voyage.legs().get(0).originLocode());
        voyage.legs().forEach(leg -> locodes.add(leg.destinationLocode()));
        return locodes;
    }
}
