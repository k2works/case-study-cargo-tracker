package com.example.routingms.application.service;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.entities.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Itinerary;
import com.example.routingms.domain.model.valueobjects.Leg;
import com.example.routingms.domain.ports.VoyageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 経路候補算出サービス
 * 指定した出発地・到着地・期限を満たす旅程候補を算出する
 */
@Service
public class RouteFinderService {

    private final VoyageRepository voyageRepository;

    public RouteFinderService(VoyageRepository voyageRepository) {
        this.voyageRepository = voyageRepository;
    }

    /**
     * 経路候補を算出する
     *
     * @param originUnlocode      出発地 UNLOCODE
     * @param destinationUnlocode 到着地 UNLOCODE
     * @param arrivalDeadline     到着期限
     * @return 旅程候補リスト（直行便優先、所要日数昇順）
     */
    public List<Itinerary> findItineraries(String originUnlocode, String destinationUnlocode,
                                            LocalDate arrivalDeadline) {
        List<Voyage> allVoyages = voyageRepository.findAll();
        List<Itinerary> result = new ArrayList<>();

        // 直行便を探す
        for (Voyage voyage : allVoyages) {
            for (CarrierMovement movement : voyage.getSchedule().getCarrierMovements()) {
                if (movement.getDepartureLocationUnlocode().equals(originUnlocode)
                        && movement.getArrivalLocationUnlocode().equals(destinationUnlocode)) {
                    if (arrivalDeadline == null
                            || !movement.getArrivalDate().toLocalDate().isAfter(arrivalDeadline)) {
                        Leg leg = new Leg(
                                voyage.getVoyageNumber().getNumber(),
                                movement.getDepartureLocationUnlocode(),
                                movement.getArrivalLocationUnlocode(),
                                movement.getDepartureDate(),
                                movement.getArrivalDate());
                        result.add(new Itinerary(List.of(leg)));
                    }
                }
            }
        }

        // 乗り継ぎ便を探す（最大1回乗り継ぎ）
        for (Voyage v1 : allVoyages) {
            for (CarrierMovement m1 : v1.getSchedule().getCarrierMovements()) {
                if (!m1.getDepartureLocationUnlocode().equals(originUnlocode)) continue;

                String midpoint = m1.getArrivalLocationUnlocode();
                if (midpoint.equals(destinationUnlocode)) continue;

                for (Voyage v2 : allVoyages) {
                    for (CarrierMovement m2 : v2.getSchedule().getCarrierMovements()) {
                        if (!m2.getDepartureLocationUnlocode().equals(midpoint)) continue;
                        if (!m2.getArrivalLocationUnlocode().equals(destinationUnlocode)) continue;
                        if (!m2.getDepartureDate().isAfter(m1.getArrivalDate())) continue;

                        if (arrivalDeadline != null
                                && m2.getArrivalDate().toLocalDate().isAfter(arrivalDeadline)) continue;

                        Leg leg1 = new Leg(
                                v1.getVoyageNumber().getNumber(),
                                m1.getDepartureLocationUnlocode(),
                                m1.getArrivalLocationUnlocode(),
                                m1.getDepartureDate(),
                                m1.getArrivalDate());
                        Leg leg2 = new Leg(
                                v2.getVoyageNumber().getNumber(),
                                m2.getDepartureLocationUnlocode(),
                                m2.getArrivalLocationUnlocode(),
                                m2.getDepartureDate(),
                                m2.getArrivalDate());
                        result.add(new Itinerary(List.of(leg1, leg2)));
                    }
                }
            }
        }

        // 直行便優先（区間数昇順）→ 所要日数昇順でソート
        result.sort(Comparator.comparingInt((Itinerary it) -> it.getLegs().size())
                .thenComparingLong(Itinerary::getDurationDays));

        return result;
    }
}
