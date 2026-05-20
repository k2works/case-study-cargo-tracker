package com.example.cargotracker.billingms.domain.model.events;

import java.math.BigDecimal;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送料金が算出・確定されたイベント（US21）。
 */
public record ChargeCalculatedEvent(
        @EventTag String invoiceId,
        BigDecimal baseAmount,
        BigDecimal discountRate,
        BigDecimal finalAmount,
        String currencyCode,
        String operatorId) { }
