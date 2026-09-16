package com.example.cargotracker.handling.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 通関申告が登録された（handlingms の内部イベント / UC21）。
 *
 * <p><b>契約にしない。</b> 申告が出たことを読む BC はいまのところ無い。
 * 他 BC へ伝わるのは状態が変わったとき（{@code CustomsStatusChangedEvent}）だけである。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「同じ申告番号で 2 度登録できない」のような状態を見る守りが素通りする。</p>
 */
public record CustomsDeclarationRegisteredEvent(
        @EventTag(key = "declarationNumber") String declarationNumber,
        String trackingNumber,
        String bookingId,
        String destinationUnLocode,
        Instant declaredAt,
        String registeredBy,
        Instant registeredAt) {
}
