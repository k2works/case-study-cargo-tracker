package com.example.cargotracker.routingms.config;

import javax.sql.DataSource;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.extension.spring.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.JdbcTokenStoreConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.jdbc.TokenSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Axon Framework 5.1 と Spring Boot 4 の JDBC 系インフラ Bean を明示的に組む（ADR-0009）。
 *
 * <p>Axon 5.1-RC2 の {@code JdbcAutoConfiguration} / {@code JdbcTransactionAutoConfiguration} は
 * Jackson 3 を前提とした {@code defaultAxonObjectMapper} を要求するため、Spring Boot 4 +
 * Jackson 2 の組み合わせでは autoconfig が機能しない。本クラスで必要な Bean をすべて手動で構成する。</p>
 *
 * <p>提供する Bean とその役割:</p>
 * <ul>
 *   <li>{@link ConnectionProvider} — Spring Tx 同期に参加する {@link SpringDataSourceConnectionProvider}</li>
 *   <li>{@link TransactionManager} — Axon の {@code TransactionManager}。
 *       {@link SpringTransactionManager} を ConnectionProvider 付きコンストラクタで生成し、
 *       PooledStreamingEventProcessor の UnitOfWork lifecycle に
 *       {@code JdbcTransactionalExecutorProvider.SUPPLIER_KEY} を bind する。</li>
 *   <li>{@link TokenSchema} — snake_case 列名に統一（Flyway V002 と一致）</li>
 *   <li>{@link TokenStore} — JDBC 永続化、内部 ObjectMapper を使う {@link JacksonConverter}</li>
 * </ul>
 */
@Configuration
public class AxonJdbcConfig {

    @Bean
    public ConnectionProvider axonConnectionProvider(DataSource dataSource) {
        return new SpringDataSourceConnectionProvider(dataSource);
    }

    @Bean
    public TransactionManager axonTransactionManager(
            PlatformTransactionManager platformTransactionManager,
            ConnectionProvider connectionProvider) {
        return new SpringTransactionManager(platformTransactionManager, null, connectionProvider);
    }

    @Bean
    public TokenSchema tokenSchema() {
        return TokenSchema.builder()
                .setTokenTable("token_entry")
                .setProcessorNameColumn("processor_name")
                .setSegmentColumn("segment")
                .setMaskColumn("mask")
                .setTokenColumn("token")
                .setTokenTypeColumn("token_type")
                .setTimestampColumn("timestamp")
                .setOwnerColumn("owner")
                .build();
    }

    @Bean
    public TokenStore tokenStore(DataSource dataSource, TokenSchema tokenSchema) {
        JdbcTokenStoreConfiguration configuration = JdbcTokenStoreConfiguration.DEFAULT
                .schema(tokenSchema);
        return new JdbcTokenStore(
                new JdbcTransactionalExecutorProvider(dataSource),
                new JacksonConverter(),
                configuration);
    }
}
