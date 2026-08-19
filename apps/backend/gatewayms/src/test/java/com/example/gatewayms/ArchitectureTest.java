package com.example.gatewayms;

import com.example.shared.architecture.HexagonalArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * gatewayms のアーキテクチャ規則。
 *
 * <p>規則の実体は shared の testFixtures にあり、全サービスで同一のものを適用する。
 * このクラスが存在しないサービスは shared の ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest {

    private static final String SERVICE = "gatewayms";
    private static final String BASE_PACKAGE = "com.example." + SERVICE;

    private final JavaClasses classes = HexagonalArchitectureRules.importProductionClasses(BASE_PACKAGE);

    // gatewayms は ADR-004 に基づき署名検証を担う唯一のサービスであるため、
    // JWT ライブラリへの依存を禁じる規則は適用しない。その代わり、
    // 「保護経路が未認証で 401 になる」ことを GatewayAuthenticationTest で検証する。

    @Test
    @DisplayName("ヘキサゴナルアーキテクチャの依存方向を守る")
    void respectsLayerDependencies() {
        for (ArchRule rule : HexagonalArchitectureRules.layerRules(BASE_PACKAGE)) {
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("他サービスのクラスを直接参照しない")
    void isIsolatedFromOtherServices() {
        HexagonalArchitectureRules.serviceIsolationRule(SERVICE).check(classes);
    }
}
