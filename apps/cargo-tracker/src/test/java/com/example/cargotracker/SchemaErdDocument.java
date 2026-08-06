package com.example.cargotracker;

import javax.sql.DataSource;
import jig.erd.JigErd;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway が構築した実スキーマから ER 図を生成する。
 *
 * <p><strong>これはテストではなくドキュメント生成である。</strong> 検証を行わないため
 * 通常の {@code test} タスクからは除外し、{@code ./gradlew jigErd} で明示的に実行する。
 *
 * <p>目的は {@code docs/design/data-model.md} の ER 図（設計）と、実際に Flyway で構築される
 * スキーマ（実装）の乖離を検出することにある。設計書の ER 図を手で更新し続けると、
 * マイグレーションを追加したのに図だけ古いままという状態に必ずなる。
 * 生成物と突き合わせれば、その乖離が目視ではなく差分として現れる。
 *
 * <p>実行には Docker（Testcontainers）と Graphviz が必要である。
 * 出力先とファイル形式は {@code src/test/resources/jig.properties} で設定する。
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class SchemaErdDocument {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cargo_tracker")
                    .withUsername("cargo_tracker")
                    .withPassword("cargo_tracker");

    @Test
    void ER図を生成する(@Autowired DataSource dataSource) {
        JigErd.run(dataSource);
    }
}
