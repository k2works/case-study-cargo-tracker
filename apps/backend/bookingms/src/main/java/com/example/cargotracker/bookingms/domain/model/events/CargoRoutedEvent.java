package com.example.cargotracker.bookingms.domain.model.events;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 経路が確定し予約に紐付けられたイベント（US11 / UC09）。
 */
public record CargoRoutedEvent(@EventTag String bookingId, CargoItinerary itinerary) {}
