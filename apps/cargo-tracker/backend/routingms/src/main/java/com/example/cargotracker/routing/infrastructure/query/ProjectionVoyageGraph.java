package com.example.cargotracker.routing.infrastructure.query;

import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routing.domain.service.VoyageGraph;
import com.example.cargotracker.routing.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投影から組む航海の接続（US08）。
 *
 * <p><b>1 回の探索につき 1 つ作る。</b> 覚えた内容を跨いで使い回すと、探索の途中で
 * 航海が更新されたときに、同じ探索の中で古い区間と新しい区間が混ざる。</p>
 *
 * <p>受入貨物種別は航海ごとに 1 度だけ引いて覚える。区間ごとに引くと、探索の 1 回が
 * 区間の数だけのクエリになる。</p>
 */
public class ProjectionVoyageGraph implements VoyageGraph {

    private final VoyageMapper voyages;
    private final Instant now;
    private final Map<String, Set<CargoType>> acceptedCache = new HashMap<>();

    public ProjectionVoyageGraph(VoyageMapper voyages, Instant now) {
        this.voyages = voyages;
        this.now = now;
    }

    @Override
    public List<TransitEdge> edgesFrom(Location location) {
        return voyages.findEdgesFrom(location.unLocode().value(), now).stream()
                .map(row -> new TransitEdge(
                        row.voyageNumber(),
                        Location.of(row.departureUnlocode()),
                        Location.of(row.arrivalUnlocode()),
                        row.departureAt(),
                        row.arrivalAt()))
                .toList();
    }

    @Override
    public Set<CargoType> acceptedCargoTypes(String voyageNumber) {
        return acceptedCache.computeIfAbsent(voyageNumber, number -> {
            List<String> names = voyages.findAcceptedCargoTypes(number);
            if (names.isEmpty()) {
                // 不変条件 4 の既定。投影に 1 行も無い航海は一般貨物のみ。
                // ここを空集合にすると、種別を選ばなかった航海が全部候補から消える。
                return Set.of(CargoType.GENERAL);
            }
            return names.stream().map(CargoType::valueOf)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        });
    }
}
