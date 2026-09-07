package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷役作業を記録した（契約イベント / UC13・US15）。handlingms → trackingms・bookingms。
 *
 * <p><b>購読側が要る値をすべて運ぶ。</b> trackingms は貨物状態を進め
 * （{@code TransportStatus#afterHandling}）、bookingms は最後の荷役を一覧に出す。
 * どちらも handlingms の DB を読まないので、ここに載らない値は使えない。</p>
 *
 * <p><b>中身は文字列・数値・日付だけ。</b> {@code HandlingType} は handlingms の型で、
 * 契約に載せると片方が値を足しただけでもう一方が復元できなくなる。名前で運ぶ。</p>
 *
 * <p><b>{@code offRoute} を載せる。</b> 予定ルート外かどうかは
 * {@code CargoSnapshot#isOffRoute} が答えるが、その写しは handlingms にしかない。
 * 購読側が判定し直せないので、<b>判定の結果</b>を運ぶ。</p>
 *
 * <p><b>{@code isFinalPort} を載せる。</b> 同じ荷降しでも、途中の港なら
 * {@code UNLOADED}、目的港なら {@code AWAITING_CLAIM} になる。この違いも旅程を
 * 持つ handlingms しか判定できない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「取り消し済みの再取り消しを断る」守りが素通りする。</p>
 *
 * @param activityId クライアントが作る冪等キー。通信断の再送で二重にしない
 * @param completedAt 作業が終わった時刻。<b>記録した時刻ではない</b>——通信不能時は
 *     紙に控えて後から入れる
 */
public record HandlingActivityRegisteredEvent(
        @EventTag(key = "activityId") String activityId,
        String trackingNumber,
        String bookingId,
        String handlingType,
        String unLocode,
        String voyageNumber,
        boolean offRoute,
        boolean finalPort,
        String operator,
        Instant completedAt,
        Instant recordedAt) {
}
