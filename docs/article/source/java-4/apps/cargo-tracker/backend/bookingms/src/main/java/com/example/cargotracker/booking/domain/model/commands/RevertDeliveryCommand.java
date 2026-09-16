package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 引き渡しの記録が取り消されたことを予約に反映する（UC14 / IT11 引き継ぎ枠 A）。
 *
 * <p><b>{@code BookingReactionHandler} が送る</b>（契約
 * {@code CargoDeliveryRevertedEvent} を購読して）。{@link MarkDeliveredCommand}
 * の打ち消しで、輸送の事実は trackingms が持ち、予約はそれを写す。</p>
 *
 * <p><b>時刻を運ばない。</b> 予約が持つのは「引取済でなくなった」という事実と
 * その理由で、いつ取り消したかは trackingms と handlingms の履歴が答える。
 * 同じ事実を 2 か所が別々に持つと、片方だけが直る。</p>
 */
public record RevertDeliveryCommand(
        @TargetEntityId String bookingId,
        String trackingNumber,
        String reason) {
}
