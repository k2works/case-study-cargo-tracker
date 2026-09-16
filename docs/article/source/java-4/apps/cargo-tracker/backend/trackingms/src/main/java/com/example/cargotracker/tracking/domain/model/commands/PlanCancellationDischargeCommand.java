package com.example.cargotracker.tracking.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * キャンセル承認の陸揚げ地を追跡に記録する（UC22 / US30 / 不変条件 2）。
 *
 * <p><b>追跡は閉じない。</b> 貨物はまだ船の上にあり、陸揚げの荷役を記録できなければ
 * ならない——ここで閉じると、降ろす作業が追跡に残らない。閉じるのは
 * <b>その港の荷降し（{@code UNLOAD}）を受けてから</b>である。</p>
 *
 * <p><b>{@code TrackingReactionHandler} が送る</b>（契約 {@code CargoCancelledEvent}
 * を購読して）。</p>
 */
public record PlanCancellationDischargeCommand(
        @TargetEntityId String trackingNumber,
        String dischargeUnLocode,
        String reason,
        String plannedBy,
        Instant plannedAt) {
}
