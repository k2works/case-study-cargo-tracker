package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 精算書発行コマンド（US23、IT7 T4.1）。
 *
 * <p>経理担当者が S24 精算書発行画面で「発行」ボタンを押下すると発行される。
 * Invoice 集約は {@code CALCULATED} 状態でのみ受理し、
 * {@link com.example.billingms.domain.services.InvoiceNumberGenerator} で invoice_number を採番、
 * {@link com.example.billingms.domain.services.PaymentDuePolicy} で支払期限を確定し、
 * {@link com.example.billingms.domain.events.InvoiceIssuedEvent} を発火する。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code billingStatus == CALCULATED} 以外では {@link IllegalStateException}</li>
 *   <li>invoice_number は当日サーバ採番で集約に渡さない（集約自身がドメインサービスを呼ぶ）</li>
 * </ul>
 *
 * @param invoiceId Invoice 識別子
 */
public record IssueInvoiceCommand(
        @TargetAggregateIdentifier String invoiceId
) {
}
