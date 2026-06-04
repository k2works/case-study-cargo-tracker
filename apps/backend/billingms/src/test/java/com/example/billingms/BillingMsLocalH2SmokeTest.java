package com.example.billingms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * billingms の local-h2 プロファイル起動を保証する SmokeTest（IT7 T1.6 前倒し）。
 *
 * <p>新サービス追加チェックリスト IT7 改訂版 §7 で確立した ApplicationContext assertion パターン。
 * Spring Context 起動 + Flyway マイグレーション（V1 Axon + V2 billing）の実行を検証する。
 * 空の {@code @Test} を SonarQube が Code Smell として検出する問題を回避するため、
 * 明示的な assertion を含む。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        }
)
@ActiveProfiles("local-h2")
class BillingMsLocalH2SmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
