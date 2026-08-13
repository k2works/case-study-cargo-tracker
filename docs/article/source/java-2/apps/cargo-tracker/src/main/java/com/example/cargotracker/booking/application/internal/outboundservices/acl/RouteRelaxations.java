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
 * <strong>Booking がすでに Routing を呼ぶ向き</strong>を持っており
 * （{@code VoyageCapacityPort}）、新しい循環は生まれない。
 *
 * <p><strong>{@code CargoRouteAssignments} は根拠にならない。</strong> あれは
 * <strong>Routing が Booking を呼ぶ</strong>向きであり（正典 {@code domain-model.md} の
 * 「呼び出し元 / 委譲先」）、逆である。**向きを取り違えた根拠は、
 * 次に BC 間依存を足す判断を誤らせる**（IT8 レビュー M1）。
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
