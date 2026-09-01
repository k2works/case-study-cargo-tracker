package com.example.trackingms.domain.model.valueobjects;

import java.time.Instant;

/**
 * 荷主へ届ける知らせ 1 件（US39）。
 *
 * <p>{@link TrackingNotice}（通知した事実）に<strong>番号を添えたもの</strong>である。
 * 番号が要るのは「どこまで読んだか」を覚えるためで、
 * {@link NoticeWatermark} と組で使う。
 *
 * @param id {@code tracking_notice.id}。<strong>採番の順序が「新しさ」の順序である</strong>
 * @param trackingNumber どの貨物の知らせか。押した先へ案内するために持つ
 * @param noticedAt 通知の時刻
 * @param message 荷主に見せる文言。<strong>社内の手がかりを書かない</strong>
 */
public record ShipperNotice(long id, TrackingNumber trackingNumber, Instant noticedAt,
        String message) {

    public ShipperNotice {
        if (id <= 0) {
            throw new IllegalArgumentException("知らせの番号は必須です");
        }
        if (trackingNumber == null) {
            throw new IllegalArgumentException("追跡番号は必須です");
        }
        if (noticedAt == null) {
            throw new IllegalArgumentException("通知の時刻は必須です");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通知の文言は必須です");
        }
    }
}
