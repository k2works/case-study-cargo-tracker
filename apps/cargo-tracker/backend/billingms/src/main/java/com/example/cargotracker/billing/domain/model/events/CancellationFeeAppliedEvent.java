package com.example.cargotracker.billing.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * キャンセル料を請求に積んだ（UC22 / US30 §受入基準 9）。
 *
 * <p><b>billingms の内部イベント。</b> 予約はキャンセル承認の時点で既に
 * {@code CANCELLED} になっており、これを読んで書く先は請求の投影だけである。</p>
 *
 * <p><b>購読側の投影が作れる分を運ぶ。</b> 投影はコマンドを読まないので、
 * 荷主・金額・明細がここに揃っていなければ、請求一覧も詳細も出せない
 * （{@code InvoiceCalculatedEvent} と同じ形）。</p>
 *
 * @param statusAtCancel キャンセル時の予約の状態。<b>明細の説明に出す</b>
 *     ——「なぜこの額なのか」は料率だけでは読めない
 * @param feeRate 当てた料率。<b>あとから料率が変わっても、出した請求は変わらない</b>
 */
public record CancellationFeeAppliedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String bookingId,
        String shipperId,
        String shipperName,
        String shipperType,
        String contractNumber,
        BigDecimal discountRate,
        String statusAtCancel,
        BigDecimal feeRate,
        BigDecimal baseAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal taxRate,
        boolean taxExempt,
        BigDecimal totalAmount,
        String currency,
        List<InvoiceCalculatedEvent.LineItem> lineItems,
        String appliedBy,
        Instant appliedAt) {
}
