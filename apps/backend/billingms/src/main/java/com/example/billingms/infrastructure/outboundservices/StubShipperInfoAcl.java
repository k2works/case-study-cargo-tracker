package com.example.billingms.infrastructure.outboundservices;

import com.example.billingms.domain.model.CorporateContract;
import com.example.billingms.domain.model.ShipperType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link ShipperInfoAcl} の暫定実装（IT7 / ADR-0015）。
 *
 * <p>cross-service の REST 連携は IT8 で本格実装する想定。IT7 では US22 法人割引適用の
 * ロジック検証に注力するため、固定値を返す Stub で進める。S23 UI サンプル（山田商事の
 * CORPORATE 15% 割引）と整合させる。</p>
 *
 * <p>FIXME(IT8): {@code RestShipperInfoAcl} を実装し、bookingms の
 * {@code GET /api/v1/shippers/{shipperId}} を呼ぶ。{@link ConditionalOnMissingBean} で
 * 本 Stub を自動的にオーバーライド可能にしてある（{@code restShipperInfoAcl} という Bean 名で）。
 * Resilience4j circuit breaker（失敗率 50% / 10 リクエスト window）+ Caffeine cache
 * （TTL 5min）+ 手動入力 fallback UI を実装する。</p>
 *
 * <p>Stub の挙動:</p>
 *
 * <ul>
 *   <li>shipperId が {@code "S-INDIVIDUAL-"} で始まる → INDIVIDUAL（割引率 0）</li>
 *   <li>その他 → CORPORATE 15%（S23 UI サンプルと整合）</li>
 * </ul>
 */
@Component
@ConditionalOnMissingBean(name = "restShipperInfoAcl")
public class StubShipperInfoAcl implements ShipperInfoAcl {

    private static final Logger log = LoggerFactory.getLogger(StubShipperInfoAcl.class);

    @Override
    public CorporateContract getContract(String shipperId) {
        log.warn("[stub-shipper-info-acl] shipperId={} に固定値を返却（IT7 暫定実装）", shipperId);
        if (shipperId != null && shipperId.startsWith("S-INDIVIDUAL-")) {
            return new CorporateContract(shipperId, ShipperType.INDIVIDUAL, BigDecimal.ZERO);
        }
        return new CorporateContract(shipperId, ShipperType.CORPORATE, new BigDecimal("0.15"));
    }
}
