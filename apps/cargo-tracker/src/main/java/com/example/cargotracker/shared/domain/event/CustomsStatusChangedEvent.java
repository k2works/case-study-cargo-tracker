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
 * @param status 変わったあとの通関状態
 * @param change いつ・誰が変えたか
 */
public record CustomsStatusChangedEvent(
        UUID bookingId,
        String trackingNumber,
        String declarationNumber,
        Status status,
        Change change) {

    /**
     * 変わったあとの通関状態。
     *
     * @param label   状態の表示名
     */
    public record Status(String label, boolean cleared, boolean held, String reason) { }

    /**
     * いつ・誰が変えたか。
     *
     * @param at 変更日時
     * @param by 変更した人
     */
    public record Change(Instant at, String by) { }

    // --- 購読側が使う名前（委譲するアクセサ）---

    /** @return 状態の表示名 */
    public String statusLabel() {
        return status.label();
    }

    /** @return 通関が完了したか */
    public boolean cleared() {
        return status.cleared();
    }

    /** @return 留置になったか */
    public boolean held() {
        return status.held();
    }

    /** @return 理由 */
    public String reason() {
        return status.reason();
    }

    /** @return 変更日時 */
    public Instant changedAt() {
        return change.at();
    }

    /** @return 変更した人 */
    public String changedBy() {
        return change.by();
    }

}
