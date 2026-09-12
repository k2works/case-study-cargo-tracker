package com.example.cargotracker.booking.domain.model.commands;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 予約を精算済にする（UC18 / US23 §受入基準 4）。
 *
 * <p><b>入金の連鎖から来る。</b> billingms の {@code PaymentRecordedEvent} を
 * {@code BookingReactionHandler} が購読して送る。画面から直接送る入口は置かない
 * ——入金を伴わない「精算済」は業務として存在しない。</p>
 *
 * <p>{@code BookingStatus.SETTLED} は IT1 から列挙にあったが、<b>遷移させる相手が
 * 本 IT で初めてできる</b>。</p>
 *
 * @param paidAmount 入金額。<b>予約の側でも「いくらで精算したか」を残す</b>
 */
public record SettleBookingCommand(
        @TargetEntityId String bookingId,
        String invoiceId,
        BigDecimal paidAmount,
        String currency,
        Instant paidAt,
        String settledBy) {
}
