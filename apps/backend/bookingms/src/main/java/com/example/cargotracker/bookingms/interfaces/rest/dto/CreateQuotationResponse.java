package com.example.cargotracker.bookingms.interfaces.rest.dto;

/**
 * 見積作成完了レスポンス DTO（US01）。
 *
 * <p>{@code status} は OFFERED（候補あり）または DRAFT（受入条件 5: 期限内ルートなし）。</p>
 */
public record CreateQuotationResponse(String quotationId, String status) {
}
