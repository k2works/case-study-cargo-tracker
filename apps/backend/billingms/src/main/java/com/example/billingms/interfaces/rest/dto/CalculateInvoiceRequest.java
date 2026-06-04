package com.example.billingms.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 輸送料金算出リクエスト DTO（US21、手動契機、IT7 タスク 2.5）。
 *
 * <p>通常は {@code CrossCargoDeliveredEventHandler} が cross-service で自動契機するが、
 * 経理担当者が手動契機する場合（S23 リトライ）の API 受領用 DTO。</p>
 */
public record CalculateInvoiceRequest(
        String bookingId,
        String shipperId,
        BigDecimal distanceKm,
        BigDecimal weightKg,
        String cargoType,
        Integer handlingCount,
        String currency
) {
}
