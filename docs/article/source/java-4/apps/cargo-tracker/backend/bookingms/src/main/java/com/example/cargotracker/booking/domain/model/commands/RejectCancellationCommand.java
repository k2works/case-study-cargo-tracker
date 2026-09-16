package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送中のキャンセル申請を却下する（UC22 / US30 §受入基準 7）。
 *
 * <p><b>予約の状態は動かさない。</b> 却下は「このまま運ぶ」という判断である。</p>
 *
 * @param reason 理由。<b>必須</b>——申請した営業が次に何をすればよいか決められない
 */
public record RejectCancellationCommand(
        @TargetEntityId String bookingId,
        String reason,
        String rejectedBy) {
}
