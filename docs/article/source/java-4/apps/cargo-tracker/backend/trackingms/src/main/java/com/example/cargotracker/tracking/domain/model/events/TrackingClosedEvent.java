package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡を閉じた（UC22 / US30 / 不変条件 9）。
 *
 * <p><b>キャンセルの陸揚げが済んだときに出る。</b> 承認の時点では閉じない——
 * 貨物はまだ船の上にあり、陸揚げの荷役を記録できなければならない。閉じるのは
 * <b>指定した港で荷降し（{@code UNLOAD}）が記録されてから</b>である。</p>
 *
 * <p><b>trackingms の内部イベントにした</b>（正典は契約イベントとして挙げていた）。
 * 予約はキャンセル承認の時点で既に {@code CANCELLED} になっており、<b>bookingms が
 * これを読んで書く先が無い</b>——読む側の無い契約を先に足さない（IT13 で
 * {@code CorporateContractAssignedEvent} に下した判断と同じ）。閉じたことを読むのは
 * 追跡詳細（S41）で、それは trackingms の投影で足りる。</p>
 *
 * @param reason なぜ閉じたか。<b>いまは {@code CANCELLED} だけ</b>だが、引取で
 *     閉じる形も将来ありうるので、閉じた理由を残す
 */
public record TrackingClosedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        String reason,
        String unLocode,
        Instant closedAt) {
}
