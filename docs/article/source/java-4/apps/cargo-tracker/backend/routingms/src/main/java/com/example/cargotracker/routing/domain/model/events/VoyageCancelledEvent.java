package com.example.cargotracker.routing.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 航海をキャンセルした（UC19）。
 *
 * <p>{@code CancelVoyageCommand} が発行する（IT5 R.1）。IT4 では守り（不変条件 5）
 * だけが先にあり、この状態へ到達する手段はイベントの直接適用しか無かった。
 * 守りだけを書いて到達できないと、その守りは本番では一度も踏まれない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * キャンセル済みかどうかを見る守りが素通りする。</p>
 */
public record VoyageCancelledEvent(
        @EventTag(key = "voyageNumber") String voyageNumber,
        String reason,
        String cancelledBy) {
}
