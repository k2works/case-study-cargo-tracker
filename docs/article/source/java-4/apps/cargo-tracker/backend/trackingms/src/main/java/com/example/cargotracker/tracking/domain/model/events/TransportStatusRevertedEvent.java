package com.example.cargotracker.tracking.domain.model.events;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 取り消された荷役の分だけ貨物状態を戻した（UC13 / 不変条件 11）。
 *
 * <p><b>「進めた」ことは消えない。</b> 履歴には進めた行と戻した行が並び、
 * 現場で何が起きたかが読める。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「戻す先が分からない」まま素通りする。</p>
 */
public record TransportStatusRevertedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        TransportStatus previousStatus,
        TransportStatus restoredStatus,
        String handlingType,
        String reason,
        String revertedBy,
        Instant revertedAt) {
}
