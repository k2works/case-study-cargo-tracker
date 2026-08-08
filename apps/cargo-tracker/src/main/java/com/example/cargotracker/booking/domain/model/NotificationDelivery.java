package com.example.cargotracker.booking.domain.model;

import java.time.Instant;

/**
 * 送信そのものの事実（US12）。
 *
 * <p>「いつ・誰が送って・どうなったか」をひと組にする。
 * <strong>結果と理由を離して持たない</strong> — 失敗なのに理由が無い、
 * 成功なのに理由がある、という組み合わせを作れなくするためである。
 *
 * @param sentAt        送信日時
 * @param sentBy        送信者
 * @param result        結果
 * @param failureReason 失敗の理由。成功のときは {@code null}
 */
public record NotificationDelivery(
        Instant sentAt, String sentBy, NotificationResult result, String failureReason) {

    public NotificationDelivery {
        if (sentAt == null) {
            throw new IllegalArgumentException("送信日時は必須です");
        }
        if (sentBy == null || sentBy.isBlank()) {
            throw new IllegalArgumentException("送信者は必須です");
        }
        if (result == NotificationResult.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("失敗の理由は必須です");
        }
        if (result == NotificationResult.SUCCEEDED) {
            failureReason = null;
        }
    }

    static NotificationDelivery succeeded(Instant sentAt, String sentBy) {
        return new NotificationDelivery(sentAt, sentBy, NotificationResult.SUCCEEDED, null);
    }

    // failed(...) は IT8 のクローズ時に削除した。
    //
    // **ADR-006 により外部へ送らないため、送信の失敗という事象が起こりえない。**
    // 本番から呼ばれないファクトリを残すと、「失敗を検知できる仕組みがある」と読めてしまう。
    // 実際に送る仕組みを入れる IT で、失敗の経路と一緒に戻す。
    // FAILED の値と列は残す（そのときに使う。data-model.md の正典）。
}
