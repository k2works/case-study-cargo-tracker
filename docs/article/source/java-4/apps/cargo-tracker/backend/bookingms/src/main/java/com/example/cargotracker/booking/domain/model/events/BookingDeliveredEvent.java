package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約の貨物が引き渡された（UC14 / US16 §受入基準 4）。
 *
 * <p><b>bookingms の内部イベント。</b> {@code cargo_summary.booking_status} の
 * 書き手は {@code Cargo} 自身のイベントだけである（domain-model.md:576）——
 * 他サービスのイベントで直接書くと、状態の書き手が 2 か所になる。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 状態を見る守り（二度届いても 1 度だけ）が素通りする。</p>
 *
 * <p>引き渡した港と時刻も運ぶ。<b>予約の画面が「いつ・どこで終わったか」を
 * 出せる分</b>を載せる（イベントは購読側の投影が作れる分を運ぶ）。</p>
 */
public record BookingDeliveredEvent(
        @EventTag(key = "bookingId") String bookingId,
        String trackingNumber,
        Instant deliveredAt,
        String location) {
}
