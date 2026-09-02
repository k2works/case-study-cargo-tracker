package com.example.cargotracker.shared.testing;

import org.axonframework.test.server.AxonServerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 統合テストの基底クラス。Axon Server（DCB 有効）と PostgreSQL を立てる。
 *
 * <p>コンテナは static にして全テストで使い回す。テストごとに立て直すと、
 * Axon Server の起動が支配的になって統合テストを書かなくなる。</p>
 *
 * <p><b>DCB を有効にする理由。</b> {@code @EventSourced(tagKey)} は DCB 前提で、
 * 無効な context では接続そのものが確立しない（IT1 スパイクで実測）。本番と同じ形で
 * 立てないと、統合テストが緑でも本番だけ動かない。</p>
 */
public abstract class AbstractAxonIntegrationTest {

    protected static final AxonServerContainer AXON_SERVER =
            new AxonServerContainer("axoniq/axonserver:2026.0.4")
                    .withDevMode(true)
                    .withDcbContext(true);

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        AXON_SERVER.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers", AXON_SERVER::getAxonServerAddress);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
