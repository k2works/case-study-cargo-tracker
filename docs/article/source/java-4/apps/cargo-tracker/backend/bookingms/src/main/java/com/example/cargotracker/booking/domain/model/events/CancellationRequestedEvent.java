package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 輸送中の予約にキャンセルが申し出られた（UC22 / US30 §受入基準 2・4）。
 *
 * <p><b>bookingms の内部イベント。</b> 申請は承認されるまで他サービスに影響しない
 * ——貨物はそのまま運ばれ続ける。外へ出るのは承認（{@code CargoCancelledEvent}）
 * からである。</p>
 *
 * <p><b>状態は動かさない。</b> 申請は「止めたい」という意思表示で、止まったことでは
 * ない。動かすと、承認前の貨物がキャンセル済として扱われる。</p>
 */
public record CancellationRequestedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String requestId,
        String reason,
        String requestedBy,
        Instant requestedAt) {
}
