package com.example.cargotracker.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 統合テストの基底クラス。
 *
 * <p><strong>SQL の正しさを検証する場所は実 PostgreSQL に固定する</strong>（ADR-003）。
 * ローカルのアプリ起動には H2 を使うが、Repository / Mapper のテストを H2 で書くと
 * 方言差（{@code TIMESTAMPTZ}・部分インデックス・{@code NUMERIC} の丸め）により
 * 「テストは緑だが本番で落ちる」状態になる。
 *
 * <p>コンテナは {@code static} フィールドで保持し、テストクラス間で共有する
 * （シングルトンコンテナ）。クラスごとに起動すると実行時間が現実的でなくなる。
 *
 * <p>テスト間のデータ独立性は各テストが担保すること。既定は {@code @Transactional} の
 * ロールバック、コミットが必要なテストは明示的なクリーンアップを用いる。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgreSQLIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cargo_tracker")
                    .withUsername("cargo_tracker")
                    .withPassword("cargo_tracker");

    static {
        POSTGRES.start();
    }

    /**
     * Security フィルタを組み込んだ MockMvc。
     *
     * <p>Spring Boot 4 の {@code @AutoConfigureMockMvc} は Security フィルタチェーンを
     * 自動では適用しない。適用しないまま認可テストを書くと、
     * <strong>すべてのリクエストが素通りして「認可が効いている」と誤認する</strong>。
     */
    protected MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc(@Autowired WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }
}
