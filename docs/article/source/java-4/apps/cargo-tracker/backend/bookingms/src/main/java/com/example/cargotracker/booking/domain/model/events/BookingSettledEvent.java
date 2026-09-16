package com.example.cargotracker.booking.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約が精算済になった（bookingms の内部イベント / US23 §受入基準 4）。
 *
 * <p><b>精算の輪が閉じる。</b> 見積（US01）から始まった一連の業務が、ここで
 * 終わる。</p>
 *
 * <p><b>請求書 ID と入金額を載せる。</b> 投影はコマンドを読まないので、
 * 予約の画面に「どの請求書のいくらで精算したか」を出せるようにする。</p>
 */
public record BookingSettledEvent(
        @EventTag(key = "bookingId") String bookingId,
        String invoiceId,
        BigDecimal paidAmount,
        String currency,
        Instant paidAt,
        String settledBy,
        Instant settledAt) {
}
