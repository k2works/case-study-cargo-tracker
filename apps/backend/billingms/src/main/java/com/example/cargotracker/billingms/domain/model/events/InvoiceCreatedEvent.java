package com.example.cargotracker.billingms.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * Invoice が作成されたイベント（US21）。
 */
public record InvoiceCreatedEvent(
        @EventTag String invoiceId,
        String bookingId,
        String shipperId) { }
