package com.example.cargotracker.shared.infrastructure.axon;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Axon の JDBC 系 Bean を明示的に組む。
 *
 * <p><b>なぜ手で組むか。</b> {@code TokenStore} は自動設定されず、無いと
 * {@code Could not find a mandatory TokenStore} で起動に失敗する（IT1 スパイク 0.2 で実測）。
 * {@code TransactionManager} は 1 つでなければならず、複数あると無音で
 * {@code NoTransactionManager} に落ちる。</p>
 *
 * <p>{@code SpringTransactionManager} を {@code ConnectionProvider} 付きで作るのは、
 * PooledStreamingEventProcessor の UnitOfWork に接続の実行者を bind するためで、
 * これが無いと {@code JdbcTokenStore} が
 * 「A connection executor must be present in the processing context」で失敗する
 * （take-4 ADR-0009 の実測）。</p>
 *
 * <p>DataSource を持たないサービス（gatewayms）では当たらないようにする。</p>
 */
@Configuration
@ConditionalOnClass(DataSource.class)
public class AxonJdbcConfiguration {

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

    /** 列名は Flyway V001 の token_entry と一致させる（data-model.md）。 */
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
        return new JdbcTokenStore(
                new JdbcTransactionalExecutorProvider(dataSource),
                new JacksonConverter(),
                JdbcTokenStoreConfiguration.DEFAULT.schema(tokenSchema));
    }
}
