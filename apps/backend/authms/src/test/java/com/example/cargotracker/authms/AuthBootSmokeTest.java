package com.example.cargotracker.authms;

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
 * <p>authms は Axon Framework を使用しないため Bean 循環参照のリスクは低いが、
 * 一貫性確保のため routingms / bookingms と同じスモークテストパターンを採用する。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("AuthApplication bootRun スモークテスト")
class AuthBootSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("bootRun と同じ Bean 構成で ApplicationContext が起動する")
    void bootRun構成でContextが起動する() {
        assertThat(context).isNotNull();
        assertThat(context.getId()).isNotNull();
    }
}
