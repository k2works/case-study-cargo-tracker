package com.example.cargotracker.routing.domain.model;

import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDateTime;
import java.util.Objects;

public class CarrierMovement {

    private final Location departureLocation;
    private final Location arrivalLocation;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final int baseFareAmount;
    private final String baseFareCurrency;

    public CarrierMovement(Location departureLocation, Location arrivalLocation,
                           LocalDateTime departureTime, LocalDateTime arrivalTime) {
        this(departureLocation, arrivalLocation, departureTime, arrivalTime, 0, "JPY");
    }

    public CarrierMovement(Location departureLocation, Location arrivalLocation,
                           LocalDateTime departureTime, LocalDateTime arrivalTime,
                           int baseFareAmount, String baseFareCurrency) {
        this.departureLocation = Objects.requireNonNull(departureLocation, "departureLocation must not be null");
        this.arrivalLocation = Objects.requireNonNull(arrivalLocation, "arrivalLocation must not be null");
        this.departureTime = Objects.requireNonNull(departureTime, "departureTime must not be null");
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime must be after departureTime");
        }
        this.baseFareAmount = baseFareAmount;
        this.baseFareCurrency = Objects.requireNonNull(baseFareCurrency, "baseFareCurrency must not be null");
    }

    public Location getDepartureLocation() {
        return departureLocation;
    }

    public Location getArrivalLocation() {
        return arrivalLocation;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public int getBaseFareAmount() {
        return baseFareAmount;
    }

    public String getBaseFareCurrency() {
        return baseFareCurrency;
    }
}
