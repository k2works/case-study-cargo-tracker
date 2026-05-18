package com.example.cargotracker.handlingms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * handlingms の Spring Boot コンテキストロード検証（IT5 TI04）。
 *
 * <p>Flyway V001/V002 が H2 上で適用されること、Axon Jdbc Config が Bean を組めることを確認する。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("HandlingApplication 起動")
class HandlingApplicationTest {

    @Test
    @DisplayName("Spring コンテキストが正常に起動する")
    void contextLoads() {
        // SpringBootTest によりコンテキストロードが成功すれば PASS
    }
}
