package com.example.billingms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * billingms（精算マイクロサービス）のエントリポイント。
 *
 * <p>Billing Context（請求書算出・法人割引・精算書発行・入金記録・督促）を担う。
 * Axon Framework 5 + CQRS + Event Sourcing 構成で、{@code Invoice} 単一集約に
 * {@code PENDING → CALCULATED → INVOICED → PAID / OVERDUE / CANCELLED}
 * のステートマシンを持つ（domain-model.md L885-958）。</p>
 *
 * <p>cross-service 連携（ADR-0012 集約発火型 + ADR-0015）:</p>
 * <ul>
 *   <li>入力: trackingms の {@code CargoDeliveredEvent} を
 *       {@code CrossCargoDeliveredEventHandler}（{@code @ProcessingGroup("cross-billing")}）が
 *       購読し、{@code CalculateInvoiceCommand} を発火。冪等化は集約内
 *       {@code if (billingStatus != null) return;} で担保</li>
 *   <li>参照: bookingms の {@code GET /api/v1/shippers/{id}} を {@code RestShipperInfoAcl}
 *       （Resilience4j circuit breaker + Caffeine cache TTL 5min + 手動入力 fallback）で取得</li>
 *   <li>出力: {@code PaymentRecordedEvent}（shared kernel）を集約発火し bookingms 側 Cargo を
 *       SETTLED に伝播（{@code cross-booking-billing} 経由）</li>
 * </ul>
 *
 * <p>IT7（US21・US22・US23）で新設。</p>
 */
@SpringBootApplication
@EnableScheduling
public class BillingMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingMsApplication.class, args);
    }
}
