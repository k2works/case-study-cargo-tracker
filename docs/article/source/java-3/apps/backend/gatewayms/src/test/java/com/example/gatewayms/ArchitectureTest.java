package com.example.gatewayms;

import com.example.shared.architecture.ServiceArchitectureTest;

/**
 * gatewayms のアーキテクチャ規則。
 *
 * <p>規則の実体と適用は shared の {@link ServiceArchitectureTest} にあり、ここで並べない。
 * 並べる形にすると、規則を足したときに写し漏れたサービスが無検査のまま残る（IT6 の実例）。
 * このクラスが存在しない・基底を継承していないサービスは、shared の
 * ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest extends ServiceArchitectureTest {

    @Override
    protected String serviceName() {
        return "gatewayms";
    }

    /** 署名検証を担う唯一のサービス（[ADR-004]）。 */
    @Override
    protected com.example.shared.architecture.HexagonalArchitectureRules.TokenHandling tokenHandling() {
        return com.example.shared.architecture.HexagonalArchitectureRules.TokenHandling.VERIFIES;
    }
}
