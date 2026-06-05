package com.example.billingms.interfaces.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Resilience4j CircuitBreaker 状態取得 API（IT8 T4.2、ADR-0015 後半 fallback UI）。
 *
 * <p>S23 請求詳細画面の「割引適用」ボタン押下時、frontend がまずこの endpoint を呼び、
 * {@code state} に応じて UI を切替える:</p>
 *
 * <ul>
 *   <li>{@code CLOSED} / {@code HALF_OPEN}: 通常の自動取得モードで POST /discount を呼ぶ</li>
 *   <li>{@code OPEN}: 手動入力フォームを表示し、割引率を入力させてから
 *       POST /discount {manualDiscountRate: 0.15} を呼ぶ</li>
 * </ul>
 *
 * <p>actuator の resilience4j endpoint を直接公開せず、frontend が必要な情報のみを
 * 安全に取得できる薄い専用 API とした（認可ロール ACCOUNTANT は IT8 後半で付与予定）。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/circuit-breakers")
public class CircuitBreakerHealthController {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerHealthController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    /**
     * 指定 Circuit Breaker の現在状態を返す。存在しない場合は CLOSED 相当（通常運用）を返す。
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getState(@PathVariable String name) {
        CircuitBreaker breaker = registry.find(name).orElse(null);
        if (breaker == null) {
            return ResponseEntity.ok(Map.of(
                    "name", name,
                    "state", "CLOSED",
                    "registered", false
            ));
        }
        CircuitBreaker.Metrics metrics = breaker.getMetrics();
        return ResponseEntity.ok(Map.of(
                "name", name,
                "state", breaker.getState().name(),
                "registered", true,
                "failureRate", metrics.getFailureRate(),
                "bufferedCalls", metrics.getNumberOfBufferedCalls(),
                "failedCalls", metrics.getNumberOfFailedCalls(),
                "successfulCalls", metrics.getNumberOfSuccessfulCalls()
        ));
    }
}
