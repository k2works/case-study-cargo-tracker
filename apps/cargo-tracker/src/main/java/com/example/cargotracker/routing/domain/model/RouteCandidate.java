package com.example.cargotracker.routing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * ルート候補を表す Read Model。
 */
public record RouteCandidate(
    String voyageNumber,
    List<String> viaLocodes,
    int transitDays,
    BigDecimal estimatedPrice,
    LocalDate estimatedArrival,
    Set<CargoType> supportedCargoTypes
) {
    public RouteCandidate {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号は null または空にできません");
        }
        if (viaLocodes == null) {
            throw new IllegalArgumentException("経由港リストは null にできません");
        }
        if (transitDays <= 0) {
            throw new IllegalArgumentException("所要日数は正の値でなければなりません");
        }
        if (estimatedPrice == null || estimatedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("概算料金は正の値でなければなりません");
        }
        if (estimatedArrival == null) {
            throw new IllegalArgumentException("推定到着日は null にできません");
        }
        if (supportedCargoTypes == null || supportedCargoTypes.isEmpty()) {
            throw new IllegalArgumentException("対応貨物種別は null または空にできません");
        }
        viaLocodes = List.copyOf(viaLocodes);
        supportedCargoTypes = Set.copyOf(supportedCargoTypes);
    }
}
