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
 * @param resultLabel   結果の表示名
 * @param resultBadge   結果のバッジ
 * @param resendable    再送できるか（失敗したものだけ）
 * @param failureReason 失敗の理由
 * @param content       送った文面そのもの
 */
public record BookingNotificationView(
        Instant sentAt,
        String sentBy,
        String recipient,
        String typeLabel,
        String resultLabel,
        String resultBadge,
        boolean resendable,
        String failureReason,
        String content) {
}
