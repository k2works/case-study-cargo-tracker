package com.example.billingms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cross-service 荷主情報 ACL の設定（IT8 T4.1 / ADR-0015 後半）。
 *
 * <p>{@code shipper-info.adapter} で実装を切替可能（stub / rest）。
 * rest 時は bookingms {@code /api/v1/shippers/{id}} を呼出して
 * {@code CorporateContract} を構築する。</p>
 *
 * @param adapter  "stub"（デフォルト、{@code StubShipperInfoAcl}）または "rest"
 * @param bookingms bookingms 接続設定（adapter=rest 時に必須）
 */
@ConfigurationProperties(prefix = "shipper-info")
public record ShipperInfoProperties(
        String adapter,
        Bookingms bookingms
) {

    public ShipperInfoProperties {
        if (adapter == null || adapter.isBlank()) {
            adapter = "stub";
        }
    }

    /**
     * bookingms 接続設定。
     *
     * @param baseUrl   bookingms のベース URL（{@code http://localhost:8081} など）
     * @param timeoutMs HTTP 接続/読込タイムアウト（ms）
     */
    public record Bookingms(
            String baseUrl,
            int timeoutMs
    ) {
        public Bookingms {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://localhost:8081";
            }
            if (timeoutMs <= 0) {
                timeoutMs = 1000;
            }
        }
    }
}
