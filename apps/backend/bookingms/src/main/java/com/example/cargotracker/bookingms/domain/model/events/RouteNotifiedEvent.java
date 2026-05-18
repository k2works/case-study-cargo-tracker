package com.example.cargotracker.bookingms.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/** 荷主への経路通知イベント（US12 / UC10）。IT4 はログ記録のみ。 */
public record RouteNotifiedEvent(@EventTag String bookingId) {}
