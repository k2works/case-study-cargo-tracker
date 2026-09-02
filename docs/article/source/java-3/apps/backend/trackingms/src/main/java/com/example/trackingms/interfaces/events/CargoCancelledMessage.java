package com.example.trackingms.interfaces.events;

import java.time.Instant;

/**
 * bookingms のイベントを受ける、<strong>trackingms 側の</strong>受け皿
 * （[ADR-025] 決定 3）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * <strong>知らない項目は無視する</strong>。
 *
 * <p><strong>理由は運ばれてこない。</strong>このお知らせが出る先は公開の追跡照会であり、
 * 社内の判断を流さないと決めている。<strong>あとから受け取れるようにもしない</strong>——
 * 受け皿に項目を足すと、送り手が載せてよいと読む。
 *
 * @param trackingNumber 追跡番号。これで自分の追跡を引く
 * @param bookingId 予約番号
 * @param cancelledAt キャンセルが確定した業務上の時刻
 * @param occurredAt 発行時刻
 */
public record CargoCancelledMessage(String trackingNumber, String bookingId, Instant cancelledAt,
        Instant occurredAt) {
}
