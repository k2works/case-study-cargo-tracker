package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 部分入金記録コマンド（IT9 A1.4 / ADR-0020 / US26）。
 *
 * <p>Stripe webhook（PaymentGatewayWebhookController）が受信した部分入金を
 * Invoice 集約に反映するためのコマンド。BalanceTracker が残額管理を担い、
 * 集約は {@link com.example.billingms.domain.model.BillingStatus#INVOICED}
 * または {@link com.example.billingms.domain.model.BillingStatus#PARTIALLY_PAID}
 * の状態で受理する。</p>
 *
 * <p>受理結果:</p>
 * <ul>
 *   <li>累積入金額 &lt; 総額: {@code PARTIALLY_PAID} 状態へ遷移（または維持）+
 *       {@link com.example.billingms.domain.events.PartialPaymentRecordedEvent} 発火</li>
 *   <li>累積入金額 = 総額: {@code PAID} 状態へ遷移 +
 *       shared {@code PaymentRecordedEvent} 発火（残額分のみ）</li>
 *   <li>累積入金額 &gt; 総額: {@link IllegalArgumentException}</li>
 * </ul>
 *
 * @param invoiceId          Invoice 識別子
 * @param paymentId          入金識別子（UUID、コントローラで採番）
 * @param paidAmount         今回の入金額（残額 ≧ paidAmount を集約が検証）
 * @param currency           通貨コード（ISO 4217 3 文字、集約通貨と一致必須）
 * @param paidAt             入金時刻
 * @param paymentMethod      支払方法（STRIPE 等、null 可）
 * @param externalReference  決済機関の取引番号（Stripe Charge ID 等、任意）
 */
@SuppressWarnings("java:S107") // Axon Command は入金属性を必要とするため許容
public record RecordPartialPaymentCommand(
        @TargetAggregateIdentifier String invoiceId,
        String paymentId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        String paymentMethod,
        String externalReference
) {
}
