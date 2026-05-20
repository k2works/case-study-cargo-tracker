package com.example.cargotracker.billingms.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 入金確認・精算完了リクエスト DTO（US23）。
 *
 * @param paidAmount    入金額
 * @param paymentMethod 決済方法（BANK_TRANSFER / CREDIT_CARD / CHECK）
 * @param operatorId    操作者 ID
 */
public record RecordPaymentRequest(BigDecimal paidAmount, String paymentMethod, String operatorId) {
}
