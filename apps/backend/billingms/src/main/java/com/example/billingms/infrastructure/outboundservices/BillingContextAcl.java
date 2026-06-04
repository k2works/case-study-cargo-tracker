package com.example.billingms.infrastructure.outboundservices;

/**
 * billingms が他コンテキストから Invoice 算出に必要な情報を集めるための ACL（ADR-0015 / IT7 タスク 2.4）。
 *
 * <p>1 つの {@code bookingId} / {@code trackingNumber} に対して、(a) 荷主情報（bookingms）、
 * (b) 確定旅程距離（routingms）、(c) 荷役作業回数（handlingms）を統合して返す。Billing Context の
 * 都合に合わせて整形した値オブジェクト（{@link BillingContextInfo}）を返すことで、
 * {@code CrossCargoDeliveredEventHandler} と他コンテキストの結合度を下げる。</p>
 *
 * <p>IT7 では {@code StubBillingContextAcl} で固定値を返す暫定実装。実 REST 連携
 * （{@code RestBillingContextAcl}: bookingms GET /api/v1/bookings/{id} + routingms GET +
 * handlingms GET の 3 並列）は IT8 持ち越し。それぞれに Resilience4j circuit breaker +
 * Caffeine cache TTL 5min を付ける（ADR-0015）。</p>
 */
public interface BillingContextAcl {

    /**
     * 指定された予約・追跡番号の Billing 用情報を取得する。
     *
     * @param bookingId      予約識別子
     * @param trackingNumber 追跡番号（荷役回数集計のキー）
     * @return Billing 算出に必要な情報の集合体
     */
    BillingContextInfo loadFor(String bookingId, String trackingNumber);
}
