package com.example.cargotracker.authms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * bootRun と同じ Bean 構成で ApplicationContext が起動できることを確認するスモークテスト。
 *
 * <p>authms は Axon Framework を使用しないため Bean 循環参照のリスクは低いが、
 * 一貫性確保のため routingms / bookingms と同じスモークテストパターンを採用する。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("AuthApplication bootRun スモークテスト")
class AuthBootSmokeTest {

    @Test
    @DisplayName("bootRun と同じ Bean 構成で ApplicationContext が起動する")
    void bootRun構成でContextが起動する() {
        // Context が起動できれば成功。
    }
}
