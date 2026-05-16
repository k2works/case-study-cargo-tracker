package com.example.cargotracker.bookingms.domain.model.commands;

/** 予約確定コマンド（US13 / UC11）。 */
public record ConfirmBookingCommand(String bookingId) {}
