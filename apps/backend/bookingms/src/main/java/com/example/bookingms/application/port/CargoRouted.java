package com.example.bookingms.application.port;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 経路が決まったことを、他のサービスへ伝える中身（[ADR-024] 決定 4）。
 *
 * <p>載せるのは<strong>相手が要るものだけ</strong>である。旅程そのものは載せない——
 * trackingms が要るのは到着の見込み 1 つで、旅程を写すと [ADR-019] の ACL と二重の
 * 写しになる。
 *
 * @param trackingNumber 追跡番号。受け手はこれで自分の集約を引く
 * @param bookingId 予約番号
 * @param estimatedArrival 到着の見込み。<strong>到着期限とは別物である</strong>
 *     ——期限は「いつまでに届けるか」、こちらは「いつ届く見込みか」
 * @param occurredAt 決まった日時
 */
public record CargoRouted(String trackingNumber, String bookingId, LocalDate estimatedArrival,
        Instant occurredAt) {
}
