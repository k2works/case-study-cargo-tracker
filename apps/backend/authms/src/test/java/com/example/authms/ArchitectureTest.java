package com.example.authms;

import com.example.shared.architecture.HexagonalArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * authms のアーキテクチャ規則。
 *
 * <p>規則の実体は shared の testFixtures にあり、全サービスで同一のものを適用する。
 * このクラスが存在しないサービスは shared の ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest {

    private static final String SERVICE = "authms";
    private static final String BASE_PACKAGE = "com.example." + SERVICE;

    private final JavaClasses classes = HexagonalArchitectureRules.importProductionClasses(BASE_PACKAGE);

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

    // authms は JWT を発行する側であり、鍵を持つ 2 サービスの 1 つである（ADR-004）。
    // したがってライブラリ依存は禁じられないが、検証を始めることは禁じられる。
    @Test
    @DisplayName("JWT を発行するが検証はしない（ADR-004: 検証は gatewayms に一元化する）")
    void issuesButDoesNotVerifyJwt() {
        HexagonalArchitectureRules.noTokenVerificationRule(SERVICE).check(classes);
    }
}
