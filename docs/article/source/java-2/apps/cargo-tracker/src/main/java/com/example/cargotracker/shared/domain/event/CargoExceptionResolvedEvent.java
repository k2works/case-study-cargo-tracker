package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 貨物の例外に対応が済んだ（US19「対応報告を送信できる」/ US20）。
 *
 * <p><strong>発生（{@link CargoExceptionRaisedEvent}）と分ける。</strong>
 * 荷主にとって「起きた」と「片づいた」は別の知らせであり、同じ種別で積むと
 * 通知履歴でどちらなのか区別できない。
 *
 * @param bookingId          予約 ID
 * @param trackingNumber     追跡番号
 * @param exceptionTypeLabel 例外種別の表示名（「遅延」など）
 * @param resolvedAt         対応が済んだ日時
 * @param statusAfterLabel   復帰した輸送状態の表示名。
 *                           <strong>「元に戻った」だけでは荷主に何も伝わらない</strong>
 * @param resolutionNotes    対応の内容（新しい到着予定日・対応方針・補償方針）
 * @param resolvedBy         対応した人
 */
public record CargoExceptionResolvedEvent(
        UUID bookingId,
        String trackingNumber,
        String exceptionTypeLabel,
        Instant resolvedAt,
        String statusAfterLabel,
        String resolutionNotes,
        String resolvedBy) {
}
