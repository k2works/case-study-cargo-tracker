package com.example.cargotracker.routing.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 航海をキャンセルした（UC19）。
 *
 * <p><b>これを発行するコマンドはまだ無い。</b> 航海のキャンセル（{@code CancelVoyageCommand}）
 * は IT5 以降で、IT4 で書くのは「キャンセル済みの航海は更新できない」という
 * 守り（不変条件 5）の側である。守りだけを書いて状態に到達できないと、その守りは
 * 集約のテストでも踏めない。到達できる形にしておき、発行側は IT5 で足す。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * キャンセル済みかどうかを見る守りが素通りする。</p>
 */
public record VoyageCancelledEvent(
        @EventTag(key = "voyageNumber") String voyageNumber,
        String reason,
        String cancelledBy) {
}
