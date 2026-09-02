package com.example.trackingms.domain.model.valueobjects;

import java.time.Instant;

/**
 * 荷主へ通知したという事実（[ADR-024] 決定 9）。
 *
 * <p><strong>メールは送っていない。</strong>これは送信の記録ではなく、
 * <strong>送信の代替</strong>である。荷主はこれを追跡照会の画面で読む。
 *
 * @param noticedAt 通知の時刻
 * @param message 荷主に見せる文言。<strong>社内の手がかりを書かない</strong>
 *     ——認証の外にある画面に出るため、作業者名や予約番号は載せない
 */
public record TrackingNotice(Instant noticedAt, String message) {

    public TrackingNotice {
        if (noticedAt == null) {
            throw new IllegalArgumentException("通知の時刻は必須です");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通知の文言は必須です");
        }
    }
}
