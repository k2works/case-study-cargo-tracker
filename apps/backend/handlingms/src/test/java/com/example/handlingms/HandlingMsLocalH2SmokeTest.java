package com.example.handlingms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * handlingms の local-h2 プロファイル起動スモークテスト。
 *
 * <p>Spring コンテキスト読み込み・Axon Framework 5 自動設定・Flyway による
 * Read Model スキーマ初期化（handling_activity / handling_itinerary_snapshot /
 * claim_verification）・Axon Kafka Extension（local-h2 では Kafka 接続失敗を許容）が
 * 一通り成功することを担保する。IT5 で handlingms を新設した直後の最小スモークテスト。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-h2")
class HandlingMsLocalH2SmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
