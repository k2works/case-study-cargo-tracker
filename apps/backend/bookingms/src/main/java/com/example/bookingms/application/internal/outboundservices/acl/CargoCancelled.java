package com.example.bookingms.application.internal.outboundservices.acl;

import java.time.Instant;

/**
 * キャンセルが確定した（US30・[ADR-025] 決定 3）。
 *
 * <p><strong>理由を載せない。</strong>このイベントが行き着く先は<strong>公開の追跡照会</strong>
 * ——認証の無い画面である。社内の判断（誰の都合で止めたか、どんな事情か）を、
 * 追跡番号を手に入れた誰もが読める場所へ流さない。
 *
 * <p>陸揚げ地も載せない。荷主に伝えるのは「キャンセルが確定した」ことであり、
 * どこで降ろすかは社内の手配である。
 *
 * @param trackingNumber 追跡番号。受け手はこれで自分の追跡を引く
 * @param bookingId 予約番号
 * @param cancelledAt キャンセルが確定した業務上の時刻
 * @param occurredAt 発行時刻
 */
public record CargoCancelled(String trackingNumber, String bookingId, Instant cancelledAt,
        Instant occurredAt) {
}
