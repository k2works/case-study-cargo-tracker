package com.example.cargotracker.billingms.domain.model.events;

/**
 * Invoice が作成されたイベント（US21）。
 */
public record InvoiceCreatedEvent(
        String invoiceId,
        String bookingId,
        String shipperId) { }
