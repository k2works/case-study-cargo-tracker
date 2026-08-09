package com.example.cargotracker.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 通関の状態が変わった（US29）。
 *
 * <p>Handling Context が発行し、Booking Context が「通関完了」の通知として記録し、
 * Tracking Context が留置のときに<strong>税関保留の例外を自動起票する</strong>。
 *
 * <p><strong>Handling から他 BC を呼ばない</strong>（ADR-012）。運ぶのは
 * 起きた事実であり命令ではない。どう解釈するかは購読側が決める（ADR-009）。
 * これは {@code domain-model.md} のビジネスルール 4 が
 * 「税関システムから自動登録される」と書いていた形の置き換えでもある
 * （ADR-006 により外部システムとは連携しない）。
 *
 * @param bookingId         予約 ID
 * @param trackingNumber    追跡番号
 * @param declarationNumber 申告番号
 * @param statusLabel       変更後の通関状態の表示名。<strong>列挙子名を運ばない</strong>
 * @param cleared           通関が下りたか
 * @param held              留置になったか
 * @param reason            そう判断した理由
 * @param changedAt         変更日時
 * @param changedBy         変更した人
 */
public record CustomsStatusChangedEvent(
        UUID bookingId,
        String trackingNumber,
        String declarationNumber,
        String statusLabel,
        boolean cleared,
        boolean held,
        String reason,
        Instant changedAt,
        String changedBy) {
}
