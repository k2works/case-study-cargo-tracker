package com.example.billingms.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 輸送料金算出済イベント（US21、domain-model.md L972）。
 *
 * <p>Invoice 集約が {@code PENDING → CALCULATED} に遷移したことを表す。本イベントを集約発火で
 * 直接 apply することで、Read Model 投影（{@code invoice} テーブル + {@code invoice_line BASIC} 行）と
 * cross-service 配信を実現する（ADR-0012 集約発火型）。</p>
 *
 * @param invoiceId     Invoice 識別子
 * @param bookingId     予約識別子
 * @param shipperId     荷主識別子
 * @param basicAmount   基本料金（FareCalculator で算出、円単位、円通貨想定）
 * @param currency      通貨コード（ISO 4217 3 文字）
 * @param calculatedAt  算出時刻（Clock 注入で決定的、テスト安定化）
 */
public record InvoiceCalculatedEvent(
        String invoiceId,
        String bookingId,
        String shipperId,
        BigDecimal basicAmount,
        String currency,
        LocalDateTime calculatedAt
) {
}
