package com.example.cargotracker.handling.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 通関状態が更新された（handlingms の内部イベント / UC21）。
 *
 * <p><b>契約（{@code CustomsStatusChangedEvent}）と 2 本立てにする。</b> 内部の
 * イベントは集約の復元に使い、契約は他 BC が読む。1 本にすると、集約の都合で形を
 * 変えたいときに購読側を巻き込むことになる（domain-model.md のコマンド表）。</p>
 *
 * <p><b>履歴はこのイベント列そのもの</b>（data-model.md）。追記専用の表を作らない。
 * だから理由・変更者・日時を落とさずに載せる（US29 §受入基準 8）。</p>
 */
public record CustomsStatusUpdatedEvent(
        @EventTag(key = "declarationNumber") String declarationNumber,
        String previousStatus,
        String status,
        String reason,
        String changedBy,
        Instant changedAt) {
}
