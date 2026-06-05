package com.example.billingms.interfaces.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CircuitBreakerHealthController} 単体テスト（IT8 T4.2、ADR-0015 後半 fallback UI）。
 */
class CircuitBreakerHealthControllerTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerHealthController controller;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build());
        controller = new CircuitBreakerHealthController(registry);
    }

    @Test
    @DisplayName("未登録の Circuit Breaker は CLOSED + registered=false を返す（前方互換）")
    void unregistered() {
        ResponseEntity<Map<String, Object>> response = controller.getState("unknown");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertThat(body).containsEntry("name", "unknown")
                .containsEntry("state", "CLOSED")
                .containsEntry("registered", false);
    }

    @Test
    @DisplayName("登録済み Circuit Breaker の現在状態を返す（初期 CLOSED）")
    void registeredClosed() {
        registry.circuitBreaker("shipperInfo");

        ResponseEntity<Map<String, Object>> response = controller.getState("shipperInfo");

        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertThat(body).containsEntry("name", "shipperInfo")
                .containsEntry("state", "CLOSED")
                .containsEntry("registered", true);
        assertThat(body).containsKeys("failureRate", "bufferedCalls", "failedCalls", "successfulCalls");
    }

    @Test
    @DisplayName("失敗率超過で OPEN へ遷移後は state=OPEN が返る（S23 で手動入力 UI 表示の判定材料）")
    void openAfterFailures() {
        CircuitBreaker breaker = registry.circuitBreaker("shipperInfo");
        // 2 回中 2 回失敗 → 失敗率 100% > 閾値 50% → OPEN
        breaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("boom"));
        breaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new RuntimeException("boom"));

        ResponseEntity<Map<String, Object>> response = controller.getState("shipperInfo");

        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertThat(body).containsEntry("state", "OPEN");
    }
}
