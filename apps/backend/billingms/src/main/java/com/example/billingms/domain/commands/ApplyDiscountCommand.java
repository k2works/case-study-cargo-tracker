package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 法人割引適用コマンド（US22、IT7 タスク 3.3）。
 *
 * <p>経理担当者が S23 請求詳細・算出画面で「割引を適用」ボタンを押下すると発行される
 * （Task 3.4 で UI 追加）。Invoice 集約は CALCULATED 状態でのみ受理し、{@link
 * com.example.billingms.domain.services.CorporateDiscountPolicy} で割引額を算出し、
 * {@link com.example.billingms.domain.events.DiscountAppliedEvent} を発火する。</p>
 *
 * <p>不変条件:</p>
 *
 * <ul>
 *   <li>{@code billingStatus == CALCULATED} 以外では IllegalStateException</li>
 *   <li>荷主契約は {@code ShipperInfoAcl}（Task 3.2）で取得する（本コマンドには shipperId のみ）</li>
 * </ul>
 *
 * @param invoiceId Invoice 識別子
 */
public record ApplyDiscountCommand(
        @TargetAggregateIdentifier String invoiceId
) {
}
