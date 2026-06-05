package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RestShipperInfoAcl} の Resilience4j + Caffeine 統合テスト
 * （IT8 T4.3 / ADR-0015 後半「WireMock タイムアウト → fallback、Caffeine TTL 検証」）。
 *
 * <p>{@code shipper-info.adapter=rest} で Spring Boot 上で実 Bean 配線し、bookingms を
 * WireMock で代理する。検証項目:</p>
 *
 * <ul>
 *   <li>HTTP 5xx を返し続けると CircuitBreaker が OPEN へ遷移し、fallback で
 *       CORPORATE / discountRate=0 が返る（S23 で手動入力 UI 表示の判定根拠）</li>
 *   <li>正常応答後、同じ shipperId への 2 回目呼出は Caffeine cache HIT で
 *       WireMock リクエストが増えない（TTL 5min within 1 test run）</li>
 * </ul>
 *
 * <p>@CircuitBreaker / @Cacheable は Spring AOP 経由で動作するため @SpringBootTest が必須。
 * 軽量化のため {@code resilience4j.circuitbreaker.shipperInfo.slidingWindowSize=3} に縮め、
 * 短い試行で OPEN まで到達させる。</p>
 */
@SpringBootTest(
        properties = {
                "shipper-info.adapter=rest",
                "shipper-info.bookingms.timeout-ms=2000",
                "resilience4j.circuitbreaker.instances.shipperInfo.slidingWindowSize=3",
                "resilience4j.circuitbreaker.instances.shipperInfo.minimumNumberOfCalls=3",
                "resilience4j.circuitbreaker.instances.shipperInfo.failureRateThreshold=50",
                "resilience4j.circuitbreaker.instances.shipperInfo.waitDurationInOpenState=30s",
                "resilience4j.circuitbreaker.instances.shipperInfo.permittedNumberOfCallsInHalfOpenState=1",
                // テストでは管理エンドポイントの自動健全性チェックを抑制
                "management.health.circuitbreakers.enabled=false"
        }
)
@AutoConfigureObservability
@DirtiesContext
class RestShipperInfoAclWireMockIT {

    private static WireMockServer wireMock;

    @Autowired
    private RestShipperInfoAcl acl;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CacheManager cacheManager;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void overrideBookingmsUrl(DynamicPropertyRegistry registry) {
        registry.add("shipper-info.bookingms.base-url",
                () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void resetState() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("shipperInfo").reset();
        var cache = cacheManager.getCache("shipperInfo");
        if (cache != null) cache.clear();
    }

    @Test
    @DisplayName("ADR-0015 T4.3: bookingms 5xx 連続 → CircuitBreaker OPEN → fallback で CORPORATE/0")
    void circuitBreakerOpensOnRepeatedFailuresAndFallsBack() {
        // sliding=3 / threshold=50% / minCalls=3 で OPEN まで 3 回の失敗が必要
        // 5xx を返すと Spring RestClient が HttpServerErrorException を投げ、CircuitBreaker が
        // failure としてカウントする。
        wireMock.stubFor(get(urlMatching("/api/v1/shippers/S-FAIL-.*"))
                .willReturn(aResponse().withStatus(503).withBody("{}").withHeader("Content-Type", "application/json")));

        // 3 回別の shipperId で呼ぶ（cache を避けるため）。最初の 2 回は WebClientResponseException で
        // failure としてカウントされ fallback、3 回目で OPEN に到達して以降は fallback。
        CorporateContract first = acl.getContract("S-FAIL-001");
        CorporateContract second = acl.getContract("S-FAIL-002");
        CorporateContract third = acl.getContract("S-FAIL-003");

        // すべて fallback：CORPORATE / discountRate=0
        for (CorporateContract c : new CorporateContract[]{first, second, third}) {
            assertThat(c.shipperType()).isEqualTo(ShipperType.CORPORATE);
            assertThat(c.discountRate()).isEqualByComparingTo("0");
        }

        // 4 回目（cache 未使用の別 shipperId）は OPEN により fallback。WireMock へのリクエストは
        // OPEN 後は飛ばないため、4 回目の呼出時に新規 HTTP 呼出が増えていないことを許容で確認する。
        int beforeOpenCallCount = wireMock.getAllServeEvents().size();
        CircuitBreaker.State state = circuitBreakerRegistry.circuitBreaker("shipperInfo").getState();
        assertThat(state).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);

        CorporateContract afterOpen = acl.getContract("S-FAIL-099");
        assertThat(afterOpen.discountRate()).isEqualByComparingTo("0");
        int afterOpenCallCount = wireMock.getAllServeEvents().size();
        assertThat(afterOpenCallCount).isEqualTo(beforeOpenCallCount);
    }

    @Test
    @DisplayName("ADR-0015 T4.3: 同じ shipperId の 2 回目呼出は Caffeine cache HIT で REST 呼出が増えない")
    void caffeineCacheHitsOnSecondCall() {
        wireMock.stubFor(get(urlMatching("/api/v1/shippers/S-CACHE-001"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "shipperId": "S-CACHE-001",
                                  "shipperType": "CORPORATE",
                                  "discountRate": 0.10
                                }
                                """)));

        CorporateContract first = acl.getContract("S-CACHE-001");
        assertThat(first.discountRate()).isEqualByComparingTo("0.10");
        int callsAfterFirst = wireMock.getAllServeEvents().size();
        assertThat(callsAfterFirst).isEqualTo(1);

        // 2 回目は cache から返る → WireMock リクエスト数が増えない
        CorporateContract second = acl.getContract("S-CACHE-001");
        assertThat(second).isEqualTo(first);
        assertThat(wireMock.getAllServeEvents().size()).isEqualTo(callsAfterFirst);
    }
}
