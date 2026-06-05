package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 予約「精算済」遷移コマンド（US23、IT7 T4.5、cross-service）。
 *
 * <p>billingms の {@code PaymentRecordedEvent} を購読した
 * {@code CrossBillingPaymentHandler}（{@code @ProcessingGroup("cross-booking-billing")}）が
 * 発行する。Cargo 集約は {@code DELIVERED} または {@code TRACKING_ISSUED} 以降の状態でのみ
 * 受理し、{@code SETTLED} 状態に遷移する。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>既に {@code SETTLED} / {@code CANCELLED} 状態ではない（冪等のためスキップ）</li>
 *   <li>{@code TRACKING_ISSUED} / {@code IN_TRANSIT} / {@code DELIVERED} 状態から遷移可能</li>
 * </ul>
 *
 * @param bookingId 予約識別子
 */
public record MarkBookingSettledCommand(
        @TargetAggregateIdentifier String bookingId
) {
}
