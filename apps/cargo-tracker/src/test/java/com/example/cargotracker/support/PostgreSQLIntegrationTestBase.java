package com.example.cargotracker.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 *
 * <p><strong>{@code @Import} でテストごとに構成を足さない。</strong> 構成が変わると
 * Spring は<strong>別のコンテキストを作る</strong>。コンテキストが増えるたびに
 * HikariCP のプールがもう 1 セット張られ、PostgreSQL の {@code max_connections} を
 * 超えて<strong>無関係なテストが「too many clients」で落ちる</strong>
 * （IT14 で実際に踏んだ）。共通で要る構成は本クラスに載せる。
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(QueryCounterConfiguration.class)
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

    /**
     * MockMvc を組み立てる。
     *
     * <p><strong>アプリケーションのフィルタも一緒に組み込む。</strong> Security だけを
     * 適用していると、横断的な防御（レートリミット等）を足しても<strong>テストからは
     * 存在しないのと同じ</strong>になる。「入れたのに働いていない」ことに気づけない形は、
     * Security フィルタを適用し忘れた場合とまったく同じ欠陥である。
     *
     * <p>{@code springSecurityFilterChain} は {@code springSecurity()} が入れるため
     * 二重に足さない。
     */
    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc(@Autowired WebApplicationContext context) {
        var builder = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity());
        context.getBeansOfType(jakarta.servlet.Filter.class).forEach((name, filter) -> {
            if (!"springSecurityFilterChain".equals(name)) {
                builder.addFilters(filter);
            }
        });
        this.mockMvc = builder.build();
    }
}
