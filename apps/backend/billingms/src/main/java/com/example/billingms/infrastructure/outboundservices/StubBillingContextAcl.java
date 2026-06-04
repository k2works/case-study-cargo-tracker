package com.example.billingms.infrastructure.outboundservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link BillingContextAcl} の暫定実装（IT7 / ADR-0015）。
 *
 * <p>cross-service の REST 連携は IT8 で本格実装する想定。IT7 では cross-service
 * の貫通検証と Invoice ステートマシンの実装に注力するため、固定値を返す Stub で進める。</p>
 *
 * <p>FIXME(IT8): {@code RestBillingContextAcl} を実装し、bookingms / routingms / handlingms
 * への並列 REST 呼び出し + Resilience4j circuit breaker + Caffeine cache (TTL 5min) +
 * 手動入力 fallback UI を実装する。{@link ConditionalOnMissingBean} で本 Stub を自動的に
 * オーバーライド可能にしてある。</p>
 */
@Component
@ConditionalOnMissingBean(name = "restBillingContextAcl")
public class StubBillingContextAcl implements BillingContextAcl {

    private static final Logger log = LoggerFactory.getLogger(StubBillingContextAcl.class);

    @Override
    public BillingContextInfo loadFor(String bookingId, String trackingNumber) {
        log.warn(
                "[stub-billing-context-acl] bookingId={} trackingNumber={} に固定値を返却（IT7 暫定実装）",
                bookingId, trackingNumber);
        return new BillingContextInfo(
                "S-STUB-001",
                new BigDecimal("1200"),
                "GENERAL",
                new BigDecimal("5300"),
                "JPTYO",
                "USNYC",
                8,
                "JPY"
        );
    }
}
