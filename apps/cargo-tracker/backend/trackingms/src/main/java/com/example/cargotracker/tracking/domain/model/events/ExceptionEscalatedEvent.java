package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 緊急の例外を上位者へ知らせた（UC16 / US20 §受入基準 3）。
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。US19 §3 の荷主への通知と
 * 同じ扱いで、残すのは「知らせた事実と時刻」だけである。</p>
 *
 * <p><b>読み口と対で出す。</b> 記録だけを積んでも誰も読めず、「知らせた」という
 * 受入基準の満たし方そのものが成り立たない（IT10 でこれを 2 回踏んだ）。
 * 投影は {@code tracking_exception.escalated_at} に写し、例外一覧（S42）を
 * 管理者にも開いて<b>緊急を先頭</b>に出す。</p>
 *
 * <p><b>緊急かどうかは載せない。</b> {@code ExceptionType#urgent} が答える
 * （不変条件 7）——このイベントが出ていること自体が緊急だった証拠である。</p>
 */
public record ExceptionEscalatedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String exceptionId,
        String exceptionType,
        Instant escalatedAt) {
}
