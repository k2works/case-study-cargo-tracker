package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.LocalDate;

/**
 * 追跡が示す行き先（US18 / ADR-012）。
 *
 * <p><strong>目的地と推定到着日は必ず一緒に動く。</strong> 経路が変われば
 * どちらも変わる。別々の引数で持ち回ると、片方だけ更新して
 * <strong>「目的地は変わったのに到着予定が古い」</strong>状態を作れてしまう
 * （IT8 で {@code reroute} を足したときに実際に近い形の問題が出ている）。
 *
 * <p><strong>Booking から問い合わせず、Tracking が写しとして持つ。</strong>
 * 問い合わせると Tracking → Booking の参照が生まれ、追跡番号の発行
 * （Booking → Tracking）と合わせてパッケージが循環する。
 *
 * @param location         目的地。経路が未確定なら {@code null}
 * @param estimatedArrival 推定到着日。経路が未確定なら {@code null}
 */
public record TrackingDestination(Location location, LocalDate estimatedArrival) {

    /** 行き先がまだ決まっていない状態。 */
    public static TrackingDestination unknown() {
        return new TrackingDestination(null, null);
    }
}
