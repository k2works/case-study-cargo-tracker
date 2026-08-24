package com.example.bookingms.infrastructure.messaging;

import java.time.Instant;

/**
 * handlingms のイベントを受ける、<strong>bookingms 側の</strong>受け皿
 * （[ADR-023] 決定 5・[ADR-025] 決定 1）。
 *
 * <p>相手の型を直接デシリアライズすると、相手のドメインの変更がこちらのコンパイルを壊す。
 * <strong>知らない項目は無視する</strong>（相手が項目を足しても、こちらは壊れない）。
 *
 * <p>trackingms にも同じ形の受け皿がある。<strong>共有しない</strong>——共有すると、
 * 片方の都合で項目を足したときにもう片方が巻き込まれる（BC の独立性）。
 *
 * @param trackingNumber 追跡番号。これで自分の予約を引く
 * @param bookingId 予約番号
 * @param type 荷役の種別
 * @param locationUnLocode 作業場所。<strong>陸揚げ地の候補「現在地の港」になる</strong>
 * @param completionTime 作業日時
 * @param voyageNumber 航海番号。受領・引取では {@code null}
 * @param offRoute 予定と違う場所での作業だったか（誤配検知の入力。US28・IT10）
 * @param occurredAt 発行時刻
 */
public record HandlingActivityRegisteredMessage(String trackingNumber, String bookingId,
        String type, String locationUnLocode, Instant completionTime, String voyageNumber,
        boolean offRoute, Instant occurredAt) {
}
