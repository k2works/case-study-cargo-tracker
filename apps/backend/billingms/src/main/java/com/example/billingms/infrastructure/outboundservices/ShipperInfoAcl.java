package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;

/**
 * 荷主契約情報 ACL（US22 / IT7 タスク 3.2、ADR-0015）。
 *
 * <p>Invoice 集約の {@code ApplyDiscountCommand} 受理時に bookingms から
 * {@link CorporateContract}（荷主種別 + 契約割引率）を取得する。{@link BillingContextAcl}
 * とは責務を分離する:</p>
 *
 * <ul>
 *   <li>{@code BillingContextAcl}: {@code CargoDeliveredEvent} 受信時の 1 回呼び出しで
 *       Invoice 算出に必要な全情報（distance / weight / cargoType / handlingCount 等）を取得</li>
 *   <li>{@code ShipperInfoAcl}（本 interface）: {@code ApplyDiscountCommand} 受理時のみ
 *       荷主契約を再取得（割引率変更時の最新値を反映するため、Cache TTL 5min）</li>
 * </ul>
 *
 * <p>IT7 では {@code StubShipperInfoAcl} で固定値を返す暫定実装。実 REST 連携
 * （{@code RestShipperInfoAcl}: bookingms {@code GET /api/v1/shippers/{id}} を呼び、
 * Resilience4j circuit breaker + Caffeine cache TTL 5min + 手動入力 fallback UI）は
 * IT8 持ち越し（ADR-0015 §2 参照）。</p>
 */
public interface ShipperInfoAcl {

    /**
     * 指定された荷主の契約情報を取得する。
     *
     * @param shipperId 荷主識別子
     * @return 荷主契約（種別 + 割引率）
     */
    CorporateContract getContract(String shipperId);
}
