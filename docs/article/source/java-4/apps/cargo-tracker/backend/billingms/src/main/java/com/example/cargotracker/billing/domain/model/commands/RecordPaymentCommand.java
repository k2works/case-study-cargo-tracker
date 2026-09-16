package com.example.cargotracker.billing.domain.model.commands;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 入金を記録する（UC18 / US23 §受入基準 3・4）。
 *
 * <p><b>決済機関との接続はスコープ外</b>（計画の注 N9）。経理担当者が通帳や
 * 入金明細を見て記録する。「連携した」と書ける実体が無いのに済ませない。</p>
 *
 * <p><b>入金日時は受け取る。</b> 記録した時刻ではなく<b>入金のあった時刻</b>が
 * 業務の事実で、記録は後日になることがある。</p>
 *
 * @param paymentId 入金の識別子。<b>投影の追記行を一意にする</b>
 *     （{@code payment.payment_id} が PK。少なくとも 1 回配送で二度入らない）
 * @param amount 入金額。<b>請求額と突き合わせる</b>——一部入金は扱わない
 */
public record RecordPaymentCommand(
        @TargetEntityId String invoiceId,
        String paymentId,
        BigDecimal amount,
        Instant paidAt,
        String recordedBy) {
}
