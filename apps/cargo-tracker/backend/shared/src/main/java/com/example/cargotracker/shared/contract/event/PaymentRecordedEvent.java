package com.example.cargotracker.shared.contract.event;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 請求書の入金を記録した（契約イベント / UC18・US23 §受入基準 4）。billingms → bookingms。
 *
 * <p><b>精算の輪が閉じる。</b> 入金を記録すると請求書が入金済になり、
 * bookingms が {@code SettleBookingCommand} で予約を「精算済」にする。
 * {@code BookingStatus.SETTLED} は IT1 から列挙にあったが、<b>遷移させる相手が
 * 本 IT で初めてできる</b>。</p>
 *
 * <p><b>購読側の投影が作れる分を運ぶ。</b> 投影はコマンドを読まないので、
 * 予約 ID・入金額・入金日時がここに揃っていなければ、予約の側は「いつ・いくら
 * 入金されて精算済になったか」を出せない。</p>
 *
 * <p><b>{@code @EventTag} は請求書 ID に付ける。</b> 送り出す側の集約は
 * {@code Invoice} である。付け忘れると集約が空のまま復元され、状態を見る守りが
 * 素通りする。</p>
 *
 * @param paymentId 入金の識別子。<b>追記系投影の行を一意にする</b>
 *     （{@code payment.payment_id} が PK。少なくとも 1 回配送で二度入らない）
 */
public record PaymentRecordedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String paymentId,
        String bookingId,
        String shipperId,
        BigDecimal amount,
        String currency,
        Instant paidAt,
        String recordedBy,
        Instant recordedAt) {
}
