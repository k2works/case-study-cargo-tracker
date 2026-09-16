package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷主へ経路を提示した（UC10 / US12）。
 *
 * <p>宛先と要約を載せ、予約詳細（S22）に通知履歴（いつ・誰に・何を）として写す。
 * <b>メール等の送信基盤は本リリースのスコープ外</b>（domain-model.md）。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「経路が決まっている予約だけ通知できる」守りが素通りする。</p>
 */
public record ShipperNotifiedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String recipientEmail,
        String summary,
        String notifiedBy,
        Instant notifiedAt) {
}
