package com.example.cargotracker.shared.infrastructure.axon;

import javax.sql.DataSource;
import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.extension.spring.jdbc.SpringDataSourceConnectionProvider;
import org.axonframework.extension.spring.messaging.unitofwork.SpringTransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.jdbc.JdbcTransactionalExecutorProvider;
import org.axonframework.conversion.Converter;
import org.axonframework.messaging.eventhandling.conversion.EventConverter;
import org.axonframework.messaging.eventhandling.deadletter.SequencedDeadLetterQueueFactory;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.axonframework.messaging.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
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

    /**
     * 退避したイベント（Dead Letter）の置き場。列名は Flyway の
     * {@code dead_letter_entry} と一致させる。
     *
     * <p><b>Axon の既定（{@code DeadLetterEntry} + camelCase）を使わない。</b>
     * この DB の表と列は snake_case で揃えており（data-model.md）、1 つだけ
     * 別の書き方が混ざると、運用で表を引く人が探せない。</p>
     *
     * <p>{@code timestamp} は既定の列名だが、型名と同じ語なので
     * {@code event_timestamp} にしている。</p>
     */
    @Bean
    public DeadLetterSchema deadLetterSchema() {
        return DeadLetterSchema.builder()
                .deadLetterTable("dead_letter_entry")
                .deadLetterIdentifierColumn("dead_letter_id")
                .processingGroupColumn("processing_group")
                .sequenceIdentifierColumn("sequence_identifier")
                .sequenceIndexColumn("sequence_index")
                .eventTypeColumn("event_type")
                .eventIdentifierColumn("event_identifier")
                .typeColumn("type")
                .timestampColumn("event_timestamp")
                .payloadColumn("payload")
                .metadataColumn("metadata")
                .aggregateTypeColumn("aggregate_type")
                .aggregateIdentifierColumn("aggregate_identifier")
                .sequenceNumberColumn("sequence_number")
                .tokenTypeColumn("token_type")
                .tokenColumn("token")
                .enqueuedAtColumn("enqueued_at")
                .lastTouchedColumn("last_touched")
                .processingStartedColumn("processing_started")
                .causeTypeColumn("cause_type")
                .causeMessageColumn("cause_message")
                .diagnosticsColumn("diagnostics")
                .build();
    }

    /**
     * 退避先そのもの。<b>Axon の自動設定は当たらない</b>ので手で組む。
     *
     * <p>{@code JdbcDeadLetterQueueAutoConfiguration} は
     * {@code @ConditionalOnBean(DataSource.class)} だが、自動設定は Spring Boot の
     * DataSource 自動設定より先に評価されるので、条件が成立しない。この Bean が
     * 無いまま DLQ を有効にすると、<b>起動時に落ちる</b>——
     * {@code DLQ is enabled for processor '...' but no SequencedDeadLetterQueueFactory
     * bean is available}（実測）。黙って退避なしで動くよりは良いが、
     * 起動しないままでは意味がないのでここで組む。</p>
     */
    @Bean
    public SequencedDeadLetterQueueFactory deadLetterQueueFactory(
            DataSource dataSource,
            EventConverter eventConverter,
            Converter genericConverter,
            DeadLetterSchema deadLetterSchema) {
        var executors = new OwnConnectionExecutorProvider(dataSource);
        return (processingGroup, configuration) -> JdbcSequencedDeadLetterQueue.builder()
                .processingGroup(processingGroup)
                .transactionalExecutorProvider(executors)
                .eventConverter(eventConverter)
                .genericConverter(genericConverter)
                .schema(deadLetterSchema)
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
