package com.example.shared.architecture;

import com.example.shared.architecture.HexagonalArchitectureRules.TokenHandling;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 全サービスのアーキテクチャ検査の基底。各サービスの ArchitectureTest はこれを継承する。
 *
 * <p><strong>規則を並べるのは適用する側ではない。</strong>サービス側が規則を 1 つずつ呼ぶ形だと、
 * 規則を足したときに 7 サービスへ手で写すことになり、写し漏れたサービスが無検査のまま残る。
 * IT6 で {@code eventPublishingOnlyInMessagingInfrastructureRule} が bookingms だけに適用され、
 * AMQP に最も広く触っている trackingms が無検査だったのは、まさにこの形だった。
 *
 * <p>ここを継承すれば、{@link HexagonalArchitectureRules#allServiceRules} に足した規則は
 * その瞬間に全サービスへ掛かる。サービス側が申告するのは「自分は誰か」だけである。
 */
public abstract class ServiceArchitectureTest {

    /** サービス名（settings.gradle のサブプロジェクト名と一致させる）。 */
    protected abstract String serviceName();

    /**
     * トークンの扱い（[ADR-004]）。既定は「扱わない」。
     *
     * <p>既定を NONE にするのは、<strong>申告し忘れたサービスが最も厳しい規則に落ちる</strong>
     * ようにするためである。逆にすると、申告漏れが免除として働く。
     */
    protected TokenHandling tokenHandling() {
        return TokenHandling.NONE;
    }

    @Test
    @DisplayName("全サービス共通のアーキテクチャ規則をすべて満たす")
    void satisfiesEveryServiceRule() {
        JavaClasses classes =
                HexagonalArchitectureRules.importProductionClasses("com.example." + serviceName());
        for (ArchRule rule : HexagonalArchitectureRules.allServiceRules(serviceName(), tokenHandling())) {
            rule.check(classes);
        }
    }
}
