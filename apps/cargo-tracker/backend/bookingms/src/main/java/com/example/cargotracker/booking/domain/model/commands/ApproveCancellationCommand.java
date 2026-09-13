package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送中のキャンセル申請を承認する（UC22 / US30 §受入基準 5・6）。
 *
 * <p><b>承認とは「どこで降ろすか」を決めること</b>である（不変条件 9）。
 * 決めずに承認しても、貨物は船の上に残る。</p>
 *
 * @param dischargeUnLocode 陸揚げ地。<b>現在地または残りの寄港地</b>（不変条件 9-2）
 */
public record ApproveCancellationCommand(
        @TargetEntityId String bookingId,
        String dischargeUnLocode,
        String reason,
        String approvedBy) {
}
