package com.example.billingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/**
 * 法人割引適用コマンド（US22、IT7 タスク 3.3 / IT8 T4.2）。
 *
 * <p>経理担当者が S23 請求詳細・算出画面で「割引を適用」ボタンを押下すると発行される
 * （IT7 T3.4 で UI 追加、IT8 T4.2 で手動入力 fallback 対応）。Invoice 集約は CALCULATED
 * 状態でのみ受理し、{@link com.example.billingms.domain.services.CorporateDiscountPolicy}
 * で割引額を算出し、{@link com.example.billingms.domain.events.DiscountAppliedEvent} を発火する。</p>
 *
 * <p>不変条件:</p>
 *
 * <ul>
 *   <li>{@code billingStatus == CALCULATED} 以外では IllegalStateException</li>
 *   <li>{@code manualDiscountRate} 未指定（null）時は {@code ShipperInfoAcl}（Task 3.2）で取得</li>
 *   <li>{@code manualDiscountRate} 指定時は ACL を呼ばず CORPORATE 扱いで直接適用
 *       （RestShipperInfoAcl の Circuit Breaker OPEN 検知後、S23 で経理担当者が手動入力するケース、IT8 T4.2）</li>
 *   <li>{@code manualDiscountRate} は 0.00〜0.30 の範囲（{@link com.example.billingms.domain.model.CorporateContract} と同じ）</li>
 * </ul>
 *
 * @param invoiceId          Invoice 識別子
 * @param manualDiscountRate 手動入力割引率（null 可、null 時は ACL 経由で自動取得）
 */
public record ApplyDiscountCommand(
        @TargetAggregateIdentifier String invoiceId,
        BigDecimal manualDiscountRate
) {

    /**
     * 既存の単一引数呼出（ACL 経由の自動取得）との後方互換。
     */
    public ApplyDiscountCommand(String invoiceId) {
        this(invoiceId, null);
    }
}
