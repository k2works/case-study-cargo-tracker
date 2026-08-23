package com.example.trackingms.infrastructure.messaging;

import java.time.Instant;

/**
 * handlingms のイベントを受ける、<strong>trackingms 側の</strong>受け皿（[ADR-023] 決定 5）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * <strong>知らない項目は無視する</strong>（相手が項目を足しても、こちらは壊れない）。
 *
 * @param trackingNumber 追跡番号。これで自分の追跡を引く
 * @param bookingId 予約番号
 * @param type 荷役の種別
 * @param locationUnLocode 作業場所
 * @param completionTime 作業日時
 * @param voyageNumber 航海番号。受領・引取では {@code null}
 * @param offRoute 予定と違う場所での作業だったか
 * @param occurredAt 発行時刻
 */
public record HandlingActivityRegisteredMessage(String trackingNumber, String bookingId,
        String type, String locationUnLocode, Instant completionTime, String voyageNumber,
        boolean offRoute, Instant occurredAt) {
}
