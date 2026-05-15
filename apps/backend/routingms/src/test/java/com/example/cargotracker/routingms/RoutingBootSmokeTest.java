package com.example.cargotracker.routingms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * bootRun と同じ Bean 構成で ApplicationContext が起動できることを確認するスモークテスト。
 *
 * <p>既存の {@code RoutingApplicationTests} は {@code @ActiveProfiles("springboot-integration-test")}
 * を併用し {@code @EventHandler} を含む Bean（{@code VoyageProjectionsEventHandler}）を
 * 除外する。これにより Bean 循環参照や Axon Event Processor 起動失敗を回避するが、
 * <strong>実際の bootRun では除外されない</strong>ため、テストでは検出されなかった
 * 起動エラーが発生し得る（実例: Axon 5.1 JacksonConverterAutoConfiguration ↔ Flyway 11
 * Converter ↔ Spring Boot 4 Jackson AutoConfiguration の循環参照、IT2 US24 で発生）。</p>
 *
 * <p>本スモークテストは <strong>{@code springboot-integration-test} profile を有効化せず</strong>、
 * bootRun と同じ Bean 構成で Context が起動するかを検証する。再発防止策。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("RoutingApplication bootRun スモークテスト")
class RoutingBootSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("bootRun と同じ Bean 構成で ApplicationContext が起動する")
    void bootRun構成でContextが起動する() {
        // Axon Event Processor は Axon Server 接続をバックグラウンドでリトライするが、
        // ApplicationContext の初期化は完了する想定。
        assertThat(context).isNotNull();
        assertThat(context.getId()).isNotNull();
    }
}
