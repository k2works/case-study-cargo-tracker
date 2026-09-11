package com.example.cargotracker.billing.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送料金を算出した（billingms の内部イベント / US21）。
 *
 * <p><b>契約にしない。</b> 算出したことを読む BC はいまのところ無い。予約が
 * 「精算済」になるのは入金のとき（US23・IT14）である。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「算出済の請求書に二度算出しない」のような状態を見る守りが素通りする。</p>
 *
 * <p><b>投影が作れる分を運ぶ。</b> 投影はコマンドを読まないので、明細も根拠も
 * ここに載せる（IT7 の教訓）。</p>
 */
public record InvoiceCalculatedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String bookingId,
        String shipperId,
        String shipperName,
        String shipperType,
        String contractNumber,
        BigDecimal discountRate,
        BigDecimal baseAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        List<LineItem> lineItems,
        String calculatedBy,
        Instant calculatedAt) {

    public InvoiceCalculatedEvent {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    /**
     * 明細の 1 行。
     *
     * @param itemType 表示の分類（{@code BASE} / {@code DISCOUNT} / {@code TAX} …）
     * @param description 根拠の文（「3 区間・近海 2.5 + 遠洋 6.0・1,200 kg・一般」）
     * @param basisExceptionId 調整行の根拠になった例外 ID（任意）
     */
    public record LineItem(
            String itemType,
            String description,
            BigDecimal amount,
            String currency,
            String basisExceptionId) {
    }
}
