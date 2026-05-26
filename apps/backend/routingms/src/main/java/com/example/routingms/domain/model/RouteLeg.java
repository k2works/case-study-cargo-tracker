package com.example.routingms.domain.model;

import java.time.LocalDateTime;

/**
 * 経路候補を構成する輸送区間（Leg、US08 / Routing Context）。
 *
 * <p>1 つの航海（Voyage）による積込港から荷降し港までの 1 区間を表す。
 * 経路候補（{@link RouteCandidate}）は 1 つ以上の Leg を順序付きで保持する。</p>
 */
public record RouteLeg(
        String voyageNumber,
        String loadUnlocode,
        String unloadUnlocode,
        LocalDateTime loadTime,
        LocalDateTime unloadTime
) {
}
