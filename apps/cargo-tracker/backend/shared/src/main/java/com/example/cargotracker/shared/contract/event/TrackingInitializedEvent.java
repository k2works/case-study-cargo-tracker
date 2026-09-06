package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡を開始した（US14）。trackingms → bookingms。<b>契約イベント</b>。
 *
 * <p>これを受けて {@code BookingReactionHandler} が連鎖を終える（{@code process_state}
 * を {@code COMPLETED} にする）。連鎖の最後の段が「届いたこと」を知る唯一の手立てで、
 * これが来ないまま 24 時間経った行が「止まった連鎖」である。</p>
 *
 * <p><b>状態を載せない。</b> 追跡を始めた直後がどの状態か（{@code NOT_RECEIVED}）は
 * trackingms の {@code TransportStatus} の話で、bookingms には別の意味の状態がある。
 * 同じ名前でも BC ごとに値と意味が違うので、列挙型も状態名も契約に出さない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると trackingms の集約は空のまま
 * 復元され、「二重に開始しない」守りが素通りする。</p>
 */
public record TrackingInitializedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        Instant initializedAt) {
}
