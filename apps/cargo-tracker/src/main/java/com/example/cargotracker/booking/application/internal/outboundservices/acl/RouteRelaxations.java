package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 経路探索で期限を緩めた事実（Booking → Routing の ACL ポート。US10 → US12）。
 *
 * <p><strong>運ぶのは素の値だけである。</strong> Routing の {@code RoutingCriteria} を
 * そのまま渡すと、Booking が Routing のモデルを知ることになる。
 *
 * <p>逆向きのポートを足す前に順方向を疑う（ADR-012 の規律）。ここでは
 * <strong>Booking がすでに Routing へ問い合わせる向き</strong>を持っており
 * （{@code CargoRouteAssignments}）、新しい循環は生まれない。
 */
public interface RouteRelaxations {

    /**
     * 期限を緩めた事実。
     *
     * @param originalDeadline 当初の希望期限
     * @param extraDays        当初から延ばした日数
     */
    record Relaxation(LocalDate originalDeadline, long extraDays) {
    }

    /**
     * 予約の経路探索で期限を緩めたか。
     *
     * @return 緩めていれば当初の期限と日数。提案が無い・緩めていなければ空
     */
    Optional<Relaxation> find(String bookingId);
}
