package com.example.cargotracker.booking.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約の精算が取り消された（UC18 / US23。IT15 引き継ぎ 3）。
 *
 * <p><b>bookingms の内部イベント。</b> {@code cargo_summary.booking_status} の
 * 書き手は {@code Cargo} 自身のイベントだけである（domain-model.md:576）。</p>
 *
 * <p><b>戻る先は引取済で確定している</b>ので載せない。精算済になれるのは引取済
 * からだけなので、導き直しにならない（{@code BookingDeliveryRevertedEvent} が
 * 戻り先を運ぶのは、引取済の手前が複数ありうるからである）。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「二度届いても 1 度だけ」が素通りする。</p>
 */
public record BookingSettlementRevertedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String invoiceId,
        String reason) {
}
