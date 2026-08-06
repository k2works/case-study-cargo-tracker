package com.example.cargotracker.estimation.infrastructure.providers;

import com.example.cargotracker.estimation.domain.model.CargoType;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.port.RouteCandidateProvider;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * RouteCandidateProvider のスタブ実装。
 * IT4 で VoyageRouteCandidateProvider（実データ連携）に差し替えられるまでの暫定実装。
 */
@Component
public class StubRouteCandidateProvider implements RouteCandidateProvider {

    @Override
    public List<RouteCandidate> findCandidates(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType
    ) {
        BigDecimal baseCost = new BigDecimal("500000");
        return List.of(
                new RouteCandidate("V001", "SGSIN", 21, baseCost),
                new RouteCandidate("V002", "HKHKG", 28, baseCost.multiply(new BigDecimal("0.96")))
        );
    }
}
