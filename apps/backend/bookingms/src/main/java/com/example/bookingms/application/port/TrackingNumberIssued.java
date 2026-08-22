package com.example.bookingms.application.port;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 追跡番号を発行したことを、他のサービスへ伝える中身（[ADR-022] 決定 2）。
 *
 * <p>載せるのは<strong>相手が自分の集約を作るのに要るもの</strong>だけである。ID だけだと
 * trackingms が bookingms へ問い合わせることになり、非同期にした意味が消える
 * （同期の依存が戻り、bookingms が落ちていると追跡が作れない）。予約の全部も載せない——
 * 載せるほど受け手が Booking の言葉に縛られる。
 *
 * <p>ここは <strong>application/port</strong> にある。ドメインもユースケースも「何を伝えるか」
 * だけを知り、AMQP か Kafka かは知らない。
 *
 * @param trackingNumber 発行した追跡番号。<strong>採番済みで渡す</strong>（[ADR-021]）
 * @param bookingId 予約番号
 * @param originUnLocode 出発地
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 到着期限
 * @param occurredAt 発行した日時
 */
public record TrackingNumberIssued(
        String trackingNumber,
        String bookingId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        Instant occurredAt) {
}
