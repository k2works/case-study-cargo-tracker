package com.example.billingms.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 法人割引適用済イベント（US22、domain-model.md L973）。
 *
 * <p>Invoice 集約が割引を適用したことを表す。集約発火型（ADR-0012）で event store に書込
 * + Kafka 配信。投影側（{@code InvoiceProjectionsEventHandler}）は invoice 行を UPDATE し
 * invoice_line に DISCOUNT 行を INSERT する。</p>
 *
 * @param invoiceId       Invoice 識別子
 * @param shipperId       荷主識別子（契約取得のキー）
 * @param discountRate    適用された割引率（0〜0.30）
 * @param discountAmount  割引額（円、負ではなく正の値、invoice_line では -discountAmount）
 * @param totalAmount     割引後の合計金額（basic_amount - discount_amount）
 * @param appliedAt       適用時刻（Clock 注入で決定的、テスト安定化）
 */
public record DiscountAppliedEvent(
        String invoiceId,
        String shipperId,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        LocalDateTime appliedAt
) {
}
