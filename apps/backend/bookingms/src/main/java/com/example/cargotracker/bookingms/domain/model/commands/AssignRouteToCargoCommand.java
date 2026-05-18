package com.example.cargotracker.bookingms.domain.model.commands;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 確定経路を予約に紐付けるコマンド（US11 / UC09）。
 */
public record AssignRouteToCargoCommand(@TargetEntityId String bookingId, CargoItinerary itinerary) {}
