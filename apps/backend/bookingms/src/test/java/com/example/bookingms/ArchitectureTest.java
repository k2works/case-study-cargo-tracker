package com.example.bookingms;

import com.example.shared.architecture.HexagonalArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * bookingms のアーキテクチャ規則。
 *
 * <p>規則の実体は shared の testFixtures にあり、全サービスで同一のものを適用する。
 * このクラスが存在しないサービスは shared の ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest {

    private static final String SERVICE = "bookingms";
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

    @Test
    @DisplayName("JWT の署名検証を行わない（ADR-004: 検証は gatewayms に一元化する）")
    void doesNotVerifyJwtSignature() {
        HexagonalArchitectureRules.noJwtDependencyRule(SERVICE).check(classes);
    }

    @Test
    @DisplayName("入力の検査を認可より先に走らせない（ADR-016）")
    void validatesAfterAuthorization() {
        HexagonalArchitectureRules.validationAfterAuthorizationRule().check(classes);
    }

    /**
     * [ADR-019] 決定 3。イベント基盤は IT6 である。
     *
     * <p>IT6 でイベントを発行するときは、この検査を同じ変更で外す。
     */
    @Test
    @DisplayName("メッセージ基盤に触るのは infrastructure.messaging だけ（ADR-022）")
    void publishesEventsOnlyFromMessagingInfrastructure() {
        HexagonalArchitectureRules.eventPublishingOnlyInMessagingInfrastructureRule().check(classes);
    }
}
