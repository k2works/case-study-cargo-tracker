package com.example.cargotracker.trackingms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * trackingms の Spring Boot コンテキストロード検証（IT6 TI05）。
 *
 * <p>Flyway V001 が H2 上で適用されること、Axon Jdbc Config が Bean を組めることを確認する。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("TrackingApplication 起動")
class TrackingApplicationTest {

    @Test
    @DisplayName("Spring コンテキストが正常に起動する")
    void contextLoads() {
        // SpringBootTest によりコンテキストロードが成功すれば PASS
    }
}
