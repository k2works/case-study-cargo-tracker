package com.example.trackingms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * trackingms の local-h2 プロファイル起動スモークテスト。
 *
 * <p>Spring コンテキスト読み込み・Axon Framework 5 自動設定・Flyway による
 * Read Model スキーマ初期化・Axon Kafka Extension（local-h2 では Kafka 接続
 * 失敗を許容）が一通り成功することを担保する。IT5 で trackingms を新設した
 * 直後の最小スモークテスト。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-h2")
class TrackingMsLocalH2SmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void localH2プロファイルでコンテキストを起動できる() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
    }
}
