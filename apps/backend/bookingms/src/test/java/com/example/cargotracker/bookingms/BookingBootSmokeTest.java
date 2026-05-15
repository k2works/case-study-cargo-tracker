package com.example.cargotracker.bookingms;

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
 * <p>既存の {@code BookingApplicationTests} は {@code @ActiveProfiles("springboot-integration-test")}
 * を併用し {@code @EventHandler} を含む {@code CargoProjectionsEventHandler} を除外する。
 * 本スモークテストは <strong>除外せず</strong> bootRun と同じ Bean 構成で起動可能か検証する。</p>
 *
 * <p>背景: US24 で routingms の bootRun が Bean 循環参照で起動失敗。SpringBootTest は
 * Bean 解決順が異なるため検出できなかった。本テストでその死角を埋める。</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@DisplayName("BookingApplication bootRun スモークテスト")
class BookingBootSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("bootRun と同じ Bean 構成で ApplicationContext が起動する")
    void bootRun構成でContextが起動する() {
        assertThat(context).isNotNull();
        assertThat(context.getId()).isNotNull();
    }
}
