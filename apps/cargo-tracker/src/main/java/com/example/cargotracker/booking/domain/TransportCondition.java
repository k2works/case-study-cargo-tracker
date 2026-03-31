package com.example.cargotracker.booking.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 輸送条件を表す値オブジェクト。
 */
public final class TransportCondition {

    private final String originLocation;
    private final String destinationLocation;
    private final LocalDate requestedPickupDate;
    private final LocalDate requestedDeliveryDate;

    public TransportCondition(String originLocation, String destinationLocation,
                               LocalDate requestedPickupDate, LocalDate requestedDeliveryDate) {
        if (originLocation == null || originLocation.isBlank()) {
            throw new IllegalArgumentException("出発地は null または空にできません");
        }
        if (destinationLocation == null || destinationLocation.isBlank()) {
            throw new IllegalArgumentException("目的地は null または空にできません");
        }
        if (requestedPickupDate == null) {
            throw new IllegalArgumentException("希望引渡日は null にできません");
        }
        if (requestedDeliveryDate == null) {
            throw new IllegalArgumentException("希望着日は null にできません");
        }
        if (!requestedDeliveryDate.isAfter(requestedPickupDate)) {
            throw new IllegalArgumentException("希望着日は希望引渡日より後でなければなりません");
        }
        this.originLocation = originLocation;
        this.destinationLocation = destinationLocation;
        this.requestedPickupDate = requestedPickupDate;
        this.requestedDeliveryDate = requestedDeliveryDate;
    }

    public String originLocation() { return originLocation; }
    public String destinationLocation() { return destinationLocation; }
    public LocalDate requestedPickupDate() { return requestedPickupDate; }
    public LocalDate requestedDeliveryDate() { return requestedDeliveryDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransportCondition that)) return false;
        return Objects.equals(originLocation, that.originLocation)
                && Objects.equals(destinationLocation, that.destinationLocation)
                && Objects.equals(requestedPickupDate, that.requestedPickupDate)
                && Objects.equals(requestedDeliveryDate, that.requestedDeliveryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originLocation, destinationLocation, requestedPickupDate, requestedDeliveryDate);
    }
}
