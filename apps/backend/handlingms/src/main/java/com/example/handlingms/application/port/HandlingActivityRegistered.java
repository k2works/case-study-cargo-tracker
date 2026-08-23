package com.example.handlingms.application.port;

import java.time.Instant;

/**
 * 荷役作業を記録したことを伝えるイベント（[ADR-023] 決定 5・[ADR-022] の型を写す）。
 *
 * <p>載せるのは<strong>受け手が自分の集約を進めるのに要るもの</strong>だけである。
 * ID だけにすると trackingms がこちらへ問い合わせることになり、非同期にした意味が消える。
 * 荷役の全部も載せない——載せるほど受け手が Handling の言葉に縛られる。
 *
 * <p><strong>これ以外のイベントは発行しない。</strong>`CargoDeliveredEvent`（billingms へ）は
 * US26（IT12）である（[ADR-023] 決定 5）。
 *
 * @param trackingNumber 追跡番号。受け手はこれで自分の追跡を引く
 * @param bookingId 予約番号
 * @param type 荷役の種別（RECEIVE / LOAD / UNLOAD / CLAIM）
 * @param locationUnLocode 作業場所
 * @param completionTime 作業日時
 * @param voyageNumber 航海番号。受領・引取では {@code null}
 * @param offRoute 予定と違う場所での作業だったか（[ADR-023] 決定 3）
 * @param occurredAt 発行時刻
 */
public record HandlingActivityRegistered(String trackingNumber, String bookingId, String type,
        String locationUnLocode, Instant completionTime, String voyageNumber, boolean offRoute,
        Instant occurredAt) {
}
