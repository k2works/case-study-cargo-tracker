package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 入金の記録が取り消されたことを予約に反映する（UC18 / US23。IT15 引き継ぎ 3）。
 *
 * <p><b>{@code BookingReactionHandler} が送る</b>（契約 {@code PaymentVoidedEvent}
 * を購読して）。{@link SettleBookingCommand} の打ち消しで、入金の事実は billingms が
 * 持ち、予約はそれを写す。</p>
 *
 * <p><b>戻る先は引取済だけである。</b> 精算済になれるのは引取済からだけなので
 * （遷移表）、戻す先を運ぶ必要がない——{@code RevertDeliveryCommand} が
 * 戻り先を運ぶのは、引取済の手前が複数ありうるからである。</p>
 */
public record RevertSettlementCommand(
        @TargetEntityId String bookingId,
        String invoiceId,
        String reason) {
}
