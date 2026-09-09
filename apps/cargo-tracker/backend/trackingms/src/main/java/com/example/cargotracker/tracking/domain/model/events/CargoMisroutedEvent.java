package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予定ルート外の荷役で誤配を検知した（UC16 / US28 §受入基準 2）。
 *
 * <p><b>bookingms の {@code BookingMisroutedEvent} とは別の名前にする</b>
 * （domain-model.md:1421）。同じ出来事を 2 つの BC がそれぞれの言葉で記録する——
 * 予約にとっては「経路を組み直す必要が出た」、追跡にとっては「貨物が予定の
 * 経路から外れた」で、次に起きることが違う。</p>
 *
 * <p><b>状態を動かすイベントとは別に出す。</b> 状態は
 * {@code TransportStatusUpdatedEvent} が {@code MISROUTED} へ進める。こちらは
 * 「誤配として扱う」という印で、投影は {@code tracking_summary.misrouted} に
 * 写す——バナー（S22・S41）と一覧の絞り込みが読む列である。</p>
 *
 * <p><b>検知した荷役を運ぶ。</b> 誤配のバナーは「いつ・どこで予定外の荷役が
 * 記録されたか」を出す（US28 §受入基準 3）。投影はコマンドを読まない。</p>
 */
public record CargoMisroutedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        String activityId,
        String unLocode,
        Instant detectedAt,
        Instant recordedAt) {
}
