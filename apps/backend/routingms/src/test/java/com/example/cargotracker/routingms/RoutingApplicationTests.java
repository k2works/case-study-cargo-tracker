package com.example.cargotracker.routingms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * RoutingApplication の Spring コンテキスト起動確認テスト。
 *
 * <p>外部リソース（Axon Server）には接続せず Bean 解決のみ確認する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@DisplayName("RoutingApplication コンテキスト起動確認")
class RoutingApplicationTests {

    @Test
    @DisplayName("Spring コンテキストが正常に起動する")
    void contextLoads() {
        // Spring コンテキストが起動できれば成功
    }
}
