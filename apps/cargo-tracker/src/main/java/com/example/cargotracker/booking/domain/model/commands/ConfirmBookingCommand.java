package com.example.cargotracker.booking.domain.model.commands;

import java.util.UUID;

public record ConfirmBookingCommand(UUID bookingId) {
}
