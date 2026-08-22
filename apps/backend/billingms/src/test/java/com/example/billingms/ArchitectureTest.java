package com.example.billingms;

import com.example.shared.architecture.HexagonalArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * billingms のアーキテクチャ規則。
 *
 * <p>規則の実体は shared の testFixtures にあり、全サービスで同一のものを適用する。
 * このクラスが存在しないサービスは shared の ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest {

    private static final String SERVICE = "billingms";
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
     * メッセージ基盤に触ってよいのは {@code infrastructure.messaging} と合成ルートだけ（[ADR-022]）。
     *
     * <p>置き場所を 1 つに決めるのは、発行・購読が「外へ出す／受け取る」操作だからである。
     * ドメインやユースケースは<strong>何を頼むか</strong>（出力ポート）だけを知り、AMQP か
     * Kafka かは知らない。直接触れると、集約の中からブローカーを呼ぶコードが生まれ、
     * テストがブローカー無しでは動かなくなる。
     */
    @Test
    @DisplayName("メッセージ基盤に触るのは infrastructure.messaging だけ（ADR-022）")
    void publishesEventsOnlyFromMessagingInfrastructure() {
        HexagonalArchitectureRules.eventPublishingOnlyInMessagingInfrastructureRule().check(classes);
    }
}
