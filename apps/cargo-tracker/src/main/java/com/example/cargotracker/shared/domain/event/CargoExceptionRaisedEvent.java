package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 貨物に例外が起きた（US19 / US20）。
 *
 * <p>Tracking Context が発行し、Booking Context が購読して<strong>荷主への通知として
 * 記録する</strong>（受入基準「荷主に発生の通知が送信される」）。ADR-006 により
 * 外部へは送らない。通知の実体は記録である（US12 で確立）。
 *
 * <p><strong>Tracking から Booking を呼ばない</strong>（ADR-012）。呼ぶと消したはずの
 * Booking ⇄ Tracking の循環が戻る。起きた事実だけを伝え、荷主に何と伝えるかは
 * Booking が決める（ADR-009）。{@link CargoStatusUpdatedEvent} と同じ形である。
 *
 * @param bookingId          予約 ID
 * @param trackingNumber     追跡番号
 * @param exceptionTypeLabel 例外種別の表示名（「遅延」など）。
 *                           <strong>列挙子名を運ばない</strong> — 受け取る側が
 *                           Tracking の語彙を解釈することになる（ADR-005）
 * @param occurredAt         例外が起きた日時
 * @param locationUnlocode   発生場所（UN/LOCODE）
 * @param description        発生の理由
 * @param escalated          管理職へのエスカレーションが要るか（US20）
 * @param raisedBy           起票した人
 */
public record CargoExceptionRaisedEvent(
        UUID bookingId,
        String trackingNumber,
        String exceptionTypeLabel,
        Instant occurredAt,
        String locationUnlocode,
        String description,
        boolean escalated,
        String raisedBy) {
}
