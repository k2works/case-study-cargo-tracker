package com.example.trackingms.interfaces.events;

import java.time.Instant;

/**
 * handlingms のイベントを受ける、<strong>trackingms 側の</strong>受け皿（US29-5）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * <strong>知らない項目は無視する</strong>。
 *
 * @param trackingNumber 追跡番号。これで自分の追跡を引く
 * @param bookingId 予約番号
 * @param declarationNumber 申告番号
 * @param fromStatus 変更前の通関状態
 * @param toStatus 変更後の通関状態
 * @param reason 変更の理由。<strong>例外の発生状況になる</strong>
 * @param changedBy 変更した利用者
 * @param changedAt 変更日時
 * @param occurredAt 発行時刻
 */
public record CustomsStatusChangedMessage(String trackingNumber, String bookingId,
        String declarationNumber, String fromStatus, String toStatus, String reason,
        String changedBy, Instant changedAt, Instant occurredAt) {
}
