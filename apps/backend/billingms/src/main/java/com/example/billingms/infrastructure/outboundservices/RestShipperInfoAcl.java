package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * {@link ShipperInfoAcl} の REST 実装（IT8 T4.1 / ADR-0015 後半）。
 *
 * <p>bookingms の {@code GET /api/v1/shippers/{shipperId}} を呼び出して
 * {@link CorporateContract} を構築する。</p>
 *
 * <p>fallback 階層（ADR-0015 §後半）:</p>
 *
 * <ol>
 *   <li><strong>Caffeine cache</strong>（{@code @Cacheable} TTL 5min）: 通常運用時の負荷低減</li>
 *   <li><strong>Circuit Breaker</strong>（{@code @CircuitBreaker name=shipperInfo}）: bookingms 障害時に
 *       OPEN → fallback メソッド経由で {@link CorporateContract#shipperType()}=CORPORATE / discountRate=0 を返却。
 *       業務上は割引なし扱いとし、S23 UI で経理担当者が割引率を手動入力可能（T4.2 で実装）。</li>
 *   <li><strong>手動入力 fallback UI</strong>: T4.2 で S23 に Circuit Breaker OPEN 検知時の手動入力フォームを追加</li>
 * </ol>
 *
 * <p>監視メトリクス:</p>
 * <ul>
 *   <li>{@code shipper.info.lookup{result=success}} - REST 成功（cache miss 時）</li>
 *   <li>{@code shipper.info.lookup{result=fallback}} - circuit OPEN / 例外時 fallback 経路</li>
 * </ul>
 */
@Component("restShipperInfoAcl")
@ConditionalOnProperty(name = "shipper-info.adapter", havingValue = "rest")
public class RestShipperInfoAcl implements ShipperInfoAcl {

    private static final Logger log = LoggerFactory.getLogger(RestShipperInfoAcl.class);

    private final RestClient bookingmsRestClient;
    private final Counter successCounter;
    private final Counter fallbackCounter;

    public RestShipperInfoAcl(@Qualifier("bookingmsRestClient") RestClient bookingmsRestClient,
                              MeterRegistry registry) {
        this.bookingmsRestClient = bookingmsRestClient;
        this.successCounter = Counter.builder("shipper.info.lookup")
                .tag("result", "success")
                .description("bookingms /api/v1/shippers/{id} REST 呼出成功（cache miss 時）")
                .register(registry);
        this.fallbackCounter = Counter.builder("shipper.info.lookup")
                .tag("result", "fallback")
                .description("Resilience4j fallback 経路に入った件数（circuit OPEN / 例外）")
                .register(registry);
    }

    @Override
    @Cacheable(value = "shipperInfo", key = "#shipperId")
    @CircuitBreaker(name = "shipperInfo", fallbackMethod = "fallback")
    public CorporateContract getContract(String shipperId) {
        log.debug("[rest-shipper-info-acl] GET /api/v1/shippers/{} 実行（cache miss）", shipperId);
        Map<String, Object> body = bookingmsRestClient.get()
                .uri("/api/v1/shippers/{shipperId}", shipperId)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        if (body == null) {
            successCounter.increment();
            return individualFallback(shipperId);
        }
        ShipperType type = parseShipperType(body.get("shipperType"));
        BigDecimal discountRate = parseDiscountRate(body.get("discountRate"), type);
        successCounter.increment();
        return new CorporateContract(shipperId, type, discountRate);
    }

    /**
     * Resilience4j fallback。bookingms 障害時は CORPORATE / discountRate=0 を返却し、
     * 業務上は割引なしで処理を継続できるようにする（S23 UI で経理担当者の手動入力を受け付け、T4.2）。
     *
     * <p>シグネチャは Resilience4j 規約により対象メソッド引数 + Throwable を末尾に取る。</p>
     */
    @SuppressWarnings("unused")
    private CorporateContract fallback(String shipperId, Throwable throwable) {
        log.warn("[rest-shipper-info-acl] fallback 経路 shipperId={} reason={}",
                shipperId, throwable.getClass().getSimpleName());
        fallbackCounter.increment();
        return new CorporateContract(shipperId, ShipperType.CORPORATE, BigDecimal.ZERO);
    }

    private CorporateContract individualFallback(String shipperId) {
        return new CorporateContract(shipperId, ShipperType.INDIVIDUAL, BigDecimal.ZERO);
    }

    private ShipperType parseShipperType(Object raw) {
        if (raw == null) {
            return ShipperType.CORPORATE;
        }
        String s = raw.toString();
        try {
            return ShipperType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            log.warn("[rest-shipper-info-acl] 未知の shipperType={} → CORPORATE 扱い", s);
            return ShipperType.CORPORATE;
        }
    }

    private BigDecimal parseDiscountRate(Object raw, ShipperType type) {
        if (type == ShipperType.INDIVIDUAL) {
            return BigDecimal.ZERO;
        }
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw.toString());
    }
}
