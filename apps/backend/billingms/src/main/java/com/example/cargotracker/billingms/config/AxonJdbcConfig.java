package com.example.cargotracker.billingms.config;

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
 * <p>trackingms / handlingms / routingms / bookingms と同方針。
 * Axon 5.1-RC2 の autoconfig が Spring Boot 4 で機能しないため、
 * 必要な Bean をすべて手動で構成する。</p>
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
