package com.example.cargotracker.bookingms.domain.model.commands;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;

/**
 * 確定経路を予約に紐付けるコマンド（US11 / UC09）。
 */
public record AssignRouteToCargoCommand(String bookingId, CargoItinerary itinerary) {}
