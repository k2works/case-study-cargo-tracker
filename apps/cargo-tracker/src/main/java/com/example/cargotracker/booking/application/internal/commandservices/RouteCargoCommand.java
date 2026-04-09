package com.example.cargotracker.booking.application.internal.commandservices;

import java.util.Objects;

public record RouteCargoCommand(String bookingId, String voyageNumber) {

    public RouteCargoCommand {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        if (bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId must not be blank");
        }
        if (voyageNumber.isBlank()) {
            throw new IllegalArgumentException("voyageNumber must not be blank");
        }
    }
}
