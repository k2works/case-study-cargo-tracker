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

        result.addAll(findDirectItineraries(allVoyages, originUnlocode, destinationUnlocode, arrivalDeadline));
        result.addAll(findConnectingItineraries(allVoyages, originUnlocode, destinationUnlocode, arrivalDeadline));

        // 直行便優先（区間数昇順）→ 所要日数昇順でソート
        result.sort(Comparator.comparingInt((Itinerary it) -> it.getLegs().size())
                .thenComparingLong(Itinerary::getDurationDays));

        return result;
    }

    private List<Itinerary> findDirectItineraries(List<Voyage> allVoyages, String originUnlocode,
                                                  String destinationUnlocode, LocalDate arrivalDeadline) {
        List<Itinerary> result = new ArrayList<>();
        for (Voyage voyage : allVoyages) {
            for (CarrierMovement movement : voyage.getSchedule().getCarrierMovements()) {
                if (isDirectMatch(movement, originUnlocode, destinationUnlocode)
                        && isWithinDeadline(movement, arrivalDeadline)) {
                    result.add(new Itinerary(List.of(toLeg(voyage, movement))));
                }
            }
        }
        return result;
    }

    private List<Itinerary> findConnectingItineraries(List<Voyage> allVoyages, String originUnlocode,
                                                       String destinationUnlocode, LocalDate arrivalDeadline) {
        List<Itinerary> result = new ArrayList<>();
        for (Voyage firstVoyage : allVoyages) {
            for (CarrierMovement firstMovement : originMovements(firstVoyage, originUnlocode)) {
                result.addAll(findConnectionsFrom(allVoyages, firstVoyage, firstMovement, destinationUnlocode,
                        arrivalDeadline));
            }
        }
        return result;
    }

    private List<CarrierMovement> originMovements(Voyage voyage, String originUnlocode) {
        return voyage.getSchedule().getCarrierMovements().stream()
                .filter(movement -> startsFromOrigin(movement, originUnlocode))
                .toList();
    }

    private List<Itinerary> findConnectionsFrom(List<Voyage> allVoyages, Voyage firstVoyage,
                                                CarrierMovement firstMovement, String destinationUnlocode,
                                                LocalDate arrivalDeadline) {
        String midpoint = firstMovement.getArrivalLocationUnlocode();
        if (midpoint.equals(destinationUnlocode)) {
            return List.of();
        }

        List<Itinerary> result = new ArrayList<>();
        for (Voyage secondVoyage : allVoyages) {
            for (CarrierMovement secondMovement : secondVoyage.getSchedule().getCarrierMovements()) {
                if (isValidConnection(firstMovement, midpoint, secondMovement, destinationUnlocode,
                        arrivalDeadline)) {
                    result.add(new Itinerary(List.of(
                            toLeg(firstVoyage, firstMovement),
                            toLeg(secondVoyage, secondMovement)
                    )));
                }
            }
        }
        return result;
    }

    private boolean isDirectMatch(CarrierMovement movement, String originUnlocode, String destinationUnlocode) {
        return movement.getDepartureLocationUnlocode().equals(originUnlocode)
                && movement.getArrivalLocationUnlocode().equals(destinationUnlocode);
    }

    private boolean startsFromOrigin(CarrierMovement movement, String originUnlocode) {
        return movement.getDepartureLocationUnlocode().equals(originUnlocode)
                && !movement.getArrivalLocationUnlocode().equals(originUnlocode);
    }

    private boolean isValidConnection(CarrierMovement firstMovement, String midpoint, CarrierMovement secondMovement,
                                      String destinationUnlocode, LocalDate arrivalDeadline) {
        return secondMovement.getDepartureLocationUnlocode().equals(midpoint)
                && secondMovement.getArrivalLocationUnlocode().equals(destinationUnlocode)
                && secondMovement.getDepartureDate().isAfter(firstMovement.getArrivalDate())
                && isWithinDeadline(secondMovement, arrivalDeadline);
    }

    private boolean isWithinDeadline(CarrierMovement movement, LocalDate arrivalDeadline) {
        return arrivalDeadline == null || !movement.getArrivalDate().toLocalDate().isAfter(arrivalDeadline);
    }

    private Leg toLeg(Voyage voyage, CarrierMovement movement) {
        return new Leg(
                voyage.getVoyageNumber().getNumber(),
                movement.getDepartureLocationUnlocode(),
                movement.getArrivalLocationUnlocode(),
                movement.getDepartureDate(),
                movement.getArrivalDate()
        );
    }
}
