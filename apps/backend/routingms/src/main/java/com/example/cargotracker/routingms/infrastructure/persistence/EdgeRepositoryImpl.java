package com.example.cargotracker.routingms.infrastructure.persistence;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.domain.ports.EdgeRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EdgeRepository の MyBatis 実装（ADR-0011）。
 *
 * <p>carrier_movement × voyage_accepted_cargo_type の JOIN 結果（複数行）を
 * 同一 movement ごとに集約して {@link TransitEdge} に変換する。</p>
 */
@Repository
public class EdgeRepositoryImpl implements EdgeRepository {

    private final EdgeMapper edgeMapper;

    public EdgeRepositoryImpl(EdgeMapper edgeMapper) {
        this.edgeMapper = edgeMapper;
    }

    @Override
    public List<TransitEdge> findAll() {
        List<EdgeRecord> rows = edgeMapper.findAllEdgeRows();
        return aggregate(rows);
    }

    private List<TransitEdge> aggregate(List<EdgeRecord> rows) {
        Map<String, EdgeBuilder> builders = new LinkedHashMap<>();
        for (EdgeRecord row : rows) {
            builders.computeIfAbsent(row.edgeKey(), k -> new EdgeBuilder(row))
                    .addCargoType(row.getCargoType());
        }
        List<TransitEdge> edges = new ArrayList<>();
        for (EdgeBuilder builder : builders.values()) {
            edges.add(builder.build());
        }
        return edges;
    }

    private static class EdgeBuilder {
        private final EdgeRecord base;
        private final Set<CargoType> cargoTypes = EnumSet.noneOf(CargoType.class);

        EdgeBuilder(EdgeRecord base) {
            this.base = base;
        }

        void addCargoType(String cargoType) {
            try {
                cargoTypes.add(CargoType.valueOf(cargoType));
            } catch (IllegalArgumentException _) {
                // DB に存在するが enum 定義にない cargo_type は無視する
            }
        }

        TransitEdge build() {
            return new TransitEdge(
                    base.getVoyageNumber(),
                    new UnLocode(base.getDepartureUnlocode()),
                    new UnLocode(base.getArrivalUnlocode()),
                    base.getDepartureTime(),
                    base.getArrivalTime(),
                    cargoTypes);
        }
    }
}
