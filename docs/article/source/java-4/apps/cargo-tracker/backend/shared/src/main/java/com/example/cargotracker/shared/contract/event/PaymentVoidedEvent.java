package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 記録した入金を取り消した（契約イベント / UC18・US23。IT15 引き継ぎ 3）。billingms → bookingms。
 *
 * <p><b>請求書の取消（{@code InvoiceVoidedEvent}）とは別の操作である。</b> 請求書は
 * 正しく、入金の記録だけが誤っている——他社の入金との取り違え、二重記録。請求書は
 * 入金済から請求済へ戻り、督促の対象に戻る。</p>
 *
 * <p><b>予約も精算済から引取済へ戻る。</b> 戻さないと、入金が無いのに精算が
 * 終わっている予約が残る。{@code PaymentRecordedEvent} と<b>向きも購読側も同じ</b>
 * なので、対で置く（記録と打ち消しは同じ経路を通る）。</p>
 *
 * <p><b>入金の識別子を運ぶ。</b> どの入金を取り消したかが分からないと、投影は
 * 行に印を付けられない（行は消さない——消すと取り消した事実そのものが残らない）。</p>
 *
 * <p><b>{@code @EventTag} は請求書 ID に付ける。</b> 送り出す側の集約は
 * {@code Invoice} である。</p>
 */
public record PaymentVoidedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String paymentId,
        String bookingId,
        String reason,
        String voidedBy,
        Instant voidedAt) {
}
