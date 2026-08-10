package com.example.cargotracker.billing.application.internal.outboundservices.acl;

/**
 * 貨物が配達完了しているかを取得する出力ポート（Billing → Tracking の ACL。US21）。
 *
 * <p><strong>必要な粒度に変換する</strong>（ADR-005）。Billing が要るのは
 * <strong>「配達完了か否か」の 1 ビット</strong>であり、9 値の {@code TransportStatus}
 * ではない。列挙型ごと運ぶと、Tracking が状態を 1 つ増やすたびに
 * Billing の判定を見直すことになる。
 *
 * <p><strong>「引取済」の判定に使う</strong>（US21 の受入基準 1）。
 * 引取が済んでいない貨物は請求できない。
 */
public interface TrackingStatusPort {

    /**
     * 引取まで完了しているか。
     *
     * @param bookingId 予約 ID（UUID の文字列表現）
     * @return 引取が完了していれば {@code true}。
     *         <strong>追跡が始まっていない貨物も {@code false}</strong>
     *         （例外にしない — 請求できないだけである）
     */
    boolean isClaimed(String bookingId);
}
