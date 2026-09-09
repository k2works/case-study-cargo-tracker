package com.example.cargotracker.booking.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約の引き渡しが取り消された（UC14 / IT11 引き継ぎ枠 A）。
 *
 * <p><b>bookingms の内部イベント。</b> {@code cargo_summary.booking_status} の
 * 書き手は {@code Cargo} 自身のイベントだけである（domain-model.md:576）。</p>
 *
 * <p><b>戻す先を載せる。</b> 投影はコマンドを読まないので、「引取済にする前は
 * 何だったか」を集約が教えないと {@code cargo_summary} を戻せない。導き直させると、
 * 集約と投影が別々の判断を持つことになる。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「二度届いても 1 度だけ」が素通りする。</p>
 */
public record BookingDeliveryRevertedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String trackingNumber,
        String restoredStatus,
        String reason) {
}
