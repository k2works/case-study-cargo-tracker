package com.example.cargotracker.booking.application.internal.queryservices;

import java.time.Instant;

/**
 * 通知履歴の 1 行（US12）。
 *
 * <p><strong>画面が判断を持たないようにする。</strong> 表示名・バッジ・再送できるかは
 * ここまでで決まっている。
 *
 * @param sentAt        送信日時
 * @param sentBy        送信者
 * @param recipient     送信先
 * @param typeLabel     種別の表示名
 * @param result         送信の結果
 * @param content       送った文面そのもの
 */
public record BookingNotificationView(
        Instant sentAt,
        String sentBy,
        String recipient,
        String typeLabel,
        Result result,
        String content) {

    /**
     * 送信の結果。
     *
     * @param label   結果の表示名
     * @param badge   結果のバッジ
     * @param failure 失敗の理由。成功なら {@code null}
     */
    public record Result(String label, String badge, String failure) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 結果の表示名 */
    public String resultLabel() {
        return result.label();
    }

    /** @return 結果のバッジ */
    public String resultBadge() {
        return result.badge();
    }

    /** @return 失敗の理由 */
    public String failureReason() {
        return result.failure();
    }

}
