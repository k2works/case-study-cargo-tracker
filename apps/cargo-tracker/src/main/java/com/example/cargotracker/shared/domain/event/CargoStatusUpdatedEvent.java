package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 貨物の輸送状態が手で更新された（US17）。
 *
 * <p>Tracking Context が発行し、Booking Context が購読して<strong>荷主への通知として
 * 記録する</strong>（US17 の受入基準「状態変更の種類に応じて荷主への通知が送信される」）。
 *
 * <p><strong>Tracking から Booking を呼ばない。</strong> 呼ぶと ADR-012 で消した
 * Booking ⇄ Tracking の循環が戻る。起きた事実だけを伝え、
 * 通知の記録を作るかどうかは Booking が決める（ADR-009）。
 *
 * <p><strong>状態が動いたときだけ発行する。</strong> 入港のように輸送状態を変えない
 * 更新で通知を作ると、荷主に知らせる中身が無い記録が積み上がる。
 *
 * @param bookingId          予約 ID
 * @param trackingNumber     追跡番号
 * @param transportStatusLabel 新しい輸送状態の表示名（「搭載中」など）。
 *                             <strong>列挙子名を運ばない</strong> — 受け取る側が
 *                             Tracking の語彙を解釈することになるため（ADR-005）
 * @param occurredAt         その出来事が起きた日時
 * @param locationUnlocode   発生場所（UN/LOCODE）
 * @param updatedBy          手で入れた人
 */
public record CargoStatusUpdatedEvent(
        UUID bookingId,
        String trackingNumber,
        String transportStatusLabel,
        Instant occurredAt,
        String locationUnlocode,
        String updatedBy) {
}
