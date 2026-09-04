package com.example.cargotracker.routing.infrastructure.query;

import com.example.cargotracker.routing.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyageQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.MovementView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import java.time.Clock;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 航海の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class RoutingQueryHandler {

    private final VoyageMapper voyages;
    private final Clock clock;

    public RoutingQueryHandler(VoyageMapper voyages, Clock clock) {
        this.voyages = voyages;
        this.clock = clock;
    }

    @QueryHandler
    public VoyageView handle(FindVoyageQuery query) {
        VoyageMapper.VoyageRow row = voyages.findByNumber(query.voyageNumber());
        return row == null ? null : toView(row);
    }

    @QueryHandler
    public VoyageListView handle(FindVoyagesQuery query) {
        int size = Math.clamp(query.size(), 1, 200);
        int offset = Math.max(query.page(), 0) * size;
        // 「出港済みを外す」の基準時刻はここで 1 度だけ決める。行ごとに now() を
        // 引くと、ページの途中で境界が動く。
        java.time.Instant now = clock.instant();
        return new VoyageListView(
                voyages.findAll(query.includeFinished(), query.cargoType(), now, size, offset)
                        .stream().map(this::toView).toList(),
                voyages.countAll(query.includeFinished(), query.cargoType(), now));
    }

    private VoyageView toView(VoyageMapper.VoyageRow row) {
        List<String> cargoTypes = voyages.findAcceptedCargoTypes(row.voyageNumber());
        List<MovementView> movements = voyages.findMovements(row.voyageNumber()).stream()
                .map(m -> new MovementView(m.movementSeq(), m.departureUnlocode(),
                        m.arrivalUnlocode(), m.departureAt(), m.arrivalAt()))
                .toList();
        return new VoyageView(
                row.voyageNumber(), row.carrierCode(), row.carrierName(), row.vesselName(),
                row.departureUnlocode(), row.arrivalUnlocode(), row.departureAt(),
                row.arrivalAt(), row.cancelled(), cargoTypes, movements);
    }
}
