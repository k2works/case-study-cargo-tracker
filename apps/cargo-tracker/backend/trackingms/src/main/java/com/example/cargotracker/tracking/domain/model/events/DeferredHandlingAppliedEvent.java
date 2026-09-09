package com.example.cargotracker.tracking.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 預かっていた荷役を適用し終えた（IT11 引き継ぎ枠 B）。
 *
 * <p><b>預かりを解いた記録。</b> これが無いと、集約は同じ荷役をリプレイのたびに
 * 預かり続け、解決するたびに再適用する。状態そのものは
 * {@code TransportStatusUpdatedEvent} が動かすので、ここでは<b>預かりから
 * 外れた事実だけ</b>を残す——1 つのイベントに 2 つの役割を持たせない。</p>
 */
public record DeferredHandlingAppliedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String activityId) {
}
