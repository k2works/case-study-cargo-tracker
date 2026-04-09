package com.example.cargotracker.estimation.infrastructure.providers;

import com.example.cargotracker.estimation.domain.model.CargoType;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.port.RouteCandidateProvider;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * VoyageRepository から実データを取得して経路候補を算出する。
 *
 * 推奨順（直行便優先・所要日数昇順）でソートして返す。
 */
@Component
@Primary
public class VoyageRouteCandidateProvider implements RouteCandidateProvider {

    private static final BigDecimal BASE_COST_PER_DAY = new BigDecimal("20000");

    private final VoyageRepository voyageRepository;

    public VoyageRouteCandidateProvider(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    @Override
    public List<RouteCandidate> findCandidates(
            Location origin, Location destination,
            LocalDate arrivalDeadline, CargoType cargoType) {

        List<Voyage> voyages = voyageRepository.findByRoute(
                origin, destination, LocalDate.now(), arrivalDeadline);

        return voyages.stream()
                .map(this::toRouteCandidate)
                .sorted(Comparator
                        .comparingInt(RouteCandidate::transitDays))
                .toList();
    }

    private RouteCandidate toRouteCandidate(Voyage voyage) {
        int durationDays = (int) voyage.getDurationDays();
        BigDecimal estimatedCost = BASE_COST_PER_DAY.multiply(new BigDecimal(durationDays));

        // 経由港: 出発地と到着地以外の寄港地を抽出（直行便は null）
        String transitPort = extractTransitPort(voyage);

        return new RouteCandidate(
                voyage.getVoyageNumber().number(),
                transitPort,
                durationDays,
                estimatedCost
        );
    }

    /**
     * 直行便以外の場合、最初の中継港 UNLOCODE を返す。
     * 直行便（CarrierMovement が 1 件）の場合は null を返す。
     */
    private String extractTransitPort(Voyage voyage) {
        var movements = voyage.getSchedule().carrierMovements();
        if (movements.size() <= 1) {
            return null;
        }
        // 先頭の到着地が中継港
        return movements.get(0).getArrivalLocation().unlocode();
    }
}
