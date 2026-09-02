package com.example.trackingms.interfaces.events;

import java.time.Instant;
import java.time.LocalDate;

/**
 * bookingms が流す「追跡番号を発行した」の受け皿（[ADR-022]）。
 *
 * <p><strong>相手の型を共有しない。</strong>直接デシリアライズすると、相手のドメインの変更が
 * こちらのコンパイルを壊す。ここで受けてから Tracking Context の言葉へ変換する
 * （REST の ACL と同じ形。[ADR-019]）。
 *
 * <p><strong>知らない項目は無視する</strong>（[ADR-022] 決定 3）。相手が項目を足しても
 * こちらは壊れない。
 */
public record TrackingNumberIssuedMessage(
        String trackingNumber,
        String bookingId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        LocalDate estimatedArrival,
        Instant occurredAt) {
}
