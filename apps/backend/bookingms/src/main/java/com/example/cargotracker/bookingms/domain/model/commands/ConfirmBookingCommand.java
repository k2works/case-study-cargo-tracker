package com.example.cargotracker.bookingms.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/** 予約確定コマンド（US13 / UC11）。 */
public record ConfirmBookingCommand(@TargetEntityId String bookingId) {}
