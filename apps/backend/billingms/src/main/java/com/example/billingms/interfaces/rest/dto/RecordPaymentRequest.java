package com.example.billingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金記録リクエスト DTO（US23 / IT7 T4.3、{@code POST /invoices/{id}/payments}）。
 *
 * <p>IT7 は完全一致のみ受理（{@code paidAmount} は集約 {@code totalAmount} と一致が必要）。
 * IT8 で部分入金 + 決済機関 webhook 統合を予定。</p>
 *
 * @param paidAmount        入金額
 * @param currency          通貨コード（ISO 4217 3 文字、集約通貨と一致が必要）
 * @param paidAt            入金時刻（null の場合はサーバ Clock）
 * @param paymentMethod     支払方法（{@code BANK_TRANSFER} / {@code CREDIT_CARD} / {@code MANUAL}、null 可）
 * @param externalReference 決済機関の取引番号（任意、IT8 で webhook 受信時に設定）
 */
public record RecordPaymentRequest(
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        String paymentMethod,
        String externalReference
) {
}
