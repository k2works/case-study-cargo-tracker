package com.example.cargotracker.booking.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 引き渡しが済んだことを予約に反映する（UC14 / US16 §受入基準 4）。
 *
 * <p><b>{@code BookingReactionHandler} が送る</b>（契約 {@code CargoDeliveredEvent}
 * を購読して）。輸送の完了は trackingms が知っており、予約はその事実を写す。</p>
 *
 * <p><b>引取済からはキャンセルできない</b>（不変条件 9）。精算だけが次に来る。</p>
 */
public record MarkDeliveredCommand(
        @TargetEntityId String bookingId,
        String trackingNumber,
        Instant deliveredAt,
        String location) {
}
