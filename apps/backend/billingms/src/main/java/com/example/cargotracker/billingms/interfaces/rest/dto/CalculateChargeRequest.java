package com.example.cargotracker.billingms.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 料金算出リクエスト DTO（US21）。
 *
 * @param baseAmount   基本料金
 * @param currencyCode 通貨コード（デフォルト: JPY）
 * @param discountRate 割引率（0.0〜0.30）
 * @param operatorId   操作者 ID
 */
public record CalculateChargeRequest(
        BigDecimal baseAmount,
        String currencyCode,
        BigDecimal discountRate,
        String operatorId) { }
