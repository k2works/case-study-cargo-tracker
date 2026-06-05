package com.example.billingms.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 法人割引適用リクエスト（US22 / IT8 T4.2）。
 *
 * <p>通常時は body 省略可（ShipperInfoAcl が bookingms から自動取得）。Circuit Breaker
 * OPEN 時のみ {@code manualDiscountRate} に経理担当者の入力値（0.00〜0.30）を渡す。</p>
 *
 * @param manualDiscountRate 手動入力割引率（null 可、null 時は ACL 経由で自動取得）
 */
public record ApplyDiscountRequest(BigDecimal manualDiscountRate) {
}
