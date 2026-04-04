package com.example.cargotracker.routing.domain.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 航海（Voyage）集約ルート。
 *
 * <p>航海番号・運送会社・対応貨物種別・航海区間（legs）を保持する。
 */
public record Voyage(
    String voyageNumber,
    String carrierName,
    Set<CargoType> supportedCargoTypes,
    List<VoyageLeg> legs
) {
    public Voyage {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("voyageNumber は null または空にできません");
        }
        if (carrierName == null || carrierName.isBlank()) {
            throw new IllegalArgumentException("carrierName は null または空にできません");
        }
        if (supportedCargoTypes == null || supportedCargoTypes.isEmpty()) {
            throw new IllegalArgumentException("supportedCargoTypes は null または空にできません");
        }
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("legs は null または空にできません");
        }
        supportedCargoTypes = Set.copyOf(supportedCargoTypes);
        legs = List.copyOf(legs);
    }

    /**
     * この航海が指定された貨物種別に対応しているかを返す。
     *
     * @param cargoType 貨物種別
     * @return 対応していれば {@code true}
     */
    public boolean supports(CargoType cargoType) {
        return supportedCargoTypes.contains(cargoType);
    }

    /**
     * この航海の最終到着日（全レグの最大到着日）を返す。
     *
     * @return 最終到着日
     */
    public LocalDate latestArrivalDate() {
        return legs.stream()
            .map(VoyageLeg::arrivalDate)
            .max(Comparator.naturalOrder())
            .orElseThrow();
    }

    /**
     * この航海の最初の出発日（先頭レグの出発日）を返す。
     *
     * @return 最初の出発日
     */
    public LocalDate firstDepartureDate() {
        return legs.get(0).departureDate();
    }
}
