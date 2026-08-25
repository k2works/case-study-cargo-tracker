package com.example.handlingms.application.port;

import java.time.Instant;

/**
 * 通関状態が変わった（US29-5・UC21）。
 *
 * <p><strong>理由を載せる。</strong>行き先は<strong>追跡管理者の画面</strong>であり、
 * 認証の内側である。何があって留め置かれたかが分からないと、担当者は税関に
 * 問い合わせられない（公開画面へ流れるキャンセルのイベントとは立場が違う）。
 *
 * @param trackingNumber 追跡番号。受け手はこれで自分の追跡を引く
 * @param bookingId 予約番号
 * @param declarationNumber 申告番号
 * @param fromStatus 変更前の通関状態
 * @param toStatus 変更後の通関状態
 * @param reason 変更の理由
 * @param changedBy 変更した利用者
 * @param changedAt 変更日時
 * @param occurredAt 発行時刻
 */
public record CustomsStatusChanged(String trackingNumber, String bookingId,
        String declarationNumber, String fromStatus, String toStatus, String reason,
        String changedBy, Instant changedAt, Instant occurredAt) {
}
