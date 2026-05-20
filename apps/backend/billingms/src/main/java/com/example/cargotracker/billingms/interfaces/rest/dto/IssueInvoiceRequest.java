package com.example.cargotracker.billingms.interfaces.rest.dto;

import java.time.LocalDate;

/**
 * 精算書発行リクエスト DTO（US23）。
 *
 * @param paymentDue 支払期限
 * @param operatorId 操作者 ID
 */
public record IssueInvoiceRequest(LocalDate paymentDue, String operatorId) {
}
