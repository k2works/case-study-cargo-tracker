package com.example.cargotracker.shared.docs;

import java.time.Duration;
import javax.sql.DataSource;
import jig.erd.JigErd;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Flyway が構築した<b>実スキーマ</b>から ER 図を生成する。
 *
 * <p>{@code docs/design/cargo-tracker/data_model.md} の ER 図は設計、ここで出るものは実装である。
 * 設計書の図を手で更新し続けると、マイグレーションを足したのに図だけ古い状態に必ずなる。
 * 生成物と突き合わせれば、その乖離が目視ではなく差分として現れる。</p>
 *
 * <p><b>Spring のコンテキストは起動しない。</b> 見たいのは Flyway が作るスキーマだけであり、
 * Axon Server まで立てると、図を 1 枚描くために本番相当の依存を揃えることになる。</p>
 *
 * <p>Database per Service（ADR-0001 決定 1）のため、図はサービスごとに分かれる。
 * サービスをまたぐ参照は論理参照で、図には現れない——それが設計上正しい姿である。</p>
 */
public final class SchemaErdGenerator {

    private SchemaErdGenerator() {
    }

    /**
     * PostgreSQL を立て、classpath の {@code db/migration} を適用し、ER 図を書き出す。
     *
     * <p>出力先と接頭辞は、呼び出し側サービスの {@code src/test/resources/jig.properties} が決める。</p>
     *
     * @param databaseName 図の対象となるデータベース名（サービスごとに分ける）
     */
    public static void generate(String databaseName) {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(databaseName)
                .withStartupTimeout(Duration.ofMinutes(3))) {
            postgres.start();

            DataSource dataSource = dataSourceOf(postgres);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JigErd.run(dataSource);
        }
    }

    private static DataSource dataSourceOf(PostgreSQLContainer<?> postgres) {
        org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
