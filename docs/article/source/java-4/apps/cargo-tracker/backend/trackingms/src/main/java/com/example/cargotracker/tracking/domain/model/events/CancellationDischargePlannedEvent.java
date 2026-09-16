package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * キャンセルの陸揚げ地が決まった（UC22 / US30 / 不変条件 2）。
 *
 * <p><b>trackingms の内部イベント。</b> 追跡の状態は動かない——貨物はまだ船の上に
 * あり、これから降ろす。<b>「どこで降ろすか」が決まっただけ</b>である。</p>
 *
 * <p><b>読み口と対で出す。</b> 荷役の担当者は「この船から降ろす貨物」を追跡詳細
 * （S41）で読むので、陸揚げ地が投影に出ていなければ、承認したことが現場に届かない。</p>
 */
public record CancellationDischargePlannedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        String dischargeUnLocode,
        String reason,
        String plannedBy,
        Instant plannedAt) {
}
