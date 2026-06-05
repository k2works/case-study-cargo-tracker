package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金記録コマンド（US23、IT7 T4.1）。
 *
 * <p>経理担当者が S23 請求詳細画面で「入金確認」ボタンを押下、または決済機関 webhook 受信時に発行される
 * （IT7 は手動 API のみ、IT8 で webhook 統合）。Invoice 集約は {@code INVOICED} または
 * {@code OVERDUE} 状態で受理し、{@link com.example.billingms.domain.events.PaymentRecordedEvent}
 * を発火する。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code billingStatus == INVOICED || billingStatus == OVERDUE} 以外では {@link IllegalStateException}</li>
 *   <li>{@code paidAmount} は集約 {@code totalAmount} と一致（IT7 は完全一致のみ、IT8 で部分入金）</li>
 *   <li>{@code currency} は集約通貨と一致</li>
 * </ul>
 *
 * @param invoiceId          Invoice 識別子
 * @param paymentId          入金識別子（UUID、コントローラで採番）
 * @param paidAmount         入金額
 * @param currency           通貨コード（ISO 4217 3 文字）
 * @param paidAt             入金時刻
 * @param paymentMethod      支払方法（{@code BANK_TRANSFER} / {@code CREDIT_CARD} / {@code MANUAL}、null 可）
 * @param externalReference  決済機関の取引番号（任意、IT8 で webhook 受信時に設定）
 */
public record RecordPaymentCommand(
        @TargetAggregateIdentifier String invoiceId,
        String paymentId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        String paymentMethod,
        String externalReference
) {
}
