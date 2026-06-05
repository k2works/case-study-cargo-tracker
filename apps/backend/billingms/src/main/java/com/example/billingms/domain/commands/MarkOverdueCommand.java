package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 督促コマンド（US23、IT7 T4.1）。
 *
 * <p>{@code OverdueScheduler}（{@code @Scheduled} cron、毎日 09:00 JST、T4.6）が
 * {@code billing_status = INVOICED AND payment_due < now()} の請求書を抽出して順次発行する。
 * Invoice 集約は {@code INVOICED} 状態でのみ受理し、
 * {@link com.example.billingms.domain.events.InvoiceOverdueEvent} を発火する。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code billingStatus == INVOICED} 以外では {@link IllegalStateException}</li>
 *   <li>支払期限超過判定は Scheduler 側で実施（集約に日付を渡さない、ADR-0012 副作用排除）</li>
 * </ul>
 *
 * @param invoiceId Invoice 識別子
 */
public record MarkOverdueCommand(
        @TargetAggregateIdentifier String invoiceId
) {
}
