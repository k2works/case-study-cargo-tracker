package com.example.cargotracker.billingms.domain.model.events;

import java.math.BigDecimal;

/**
 * 輸送料金が算出・確定されたイベント（US21）。
 */
public record ChargeCalculatedEvent(
        String invoiceId,
        BigDecimal baseAmount,
        BigDecimal discountRate,
        BigDecimal finalAmount,
        String currencyCode,
        String operatorId) { }
