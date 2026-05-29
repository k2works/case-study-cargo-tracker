package com.example.trackingms.interfaces.events;

import com.example.shared.events.HandlingActivityRegisteredEvent;
import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service 荷役連携（US15 / US16 / ADR-0009 / ADR-0011 / IT5 3.5）の Kafka 疎通統合テスト
 * （handlingms → trackingms 方向）。
 *
 * <p>Testcontainers Kafka を起動し、handlingms が発行する想定の
 * shared {@link HandlingActivityRegisteredEvent} を Event Bus に流すと、
 * KafkaPublisher → cargo-events トピック → StreamableKafkaMessageSource →
 * {@code handling-activity-events} tracking プロセッサ →
 * {@code HandlingActivityRegisteredEventHandler} → HandlingType に応じた
 * {@code UpdateTransportStatusCommand} → {@code TrackingActivity} 集約の
 * {@code TransportStatusUpdatedEvent} → {@code tracking_summary} 投影 の物理経路を通り、
 * {@code current_status} が遷移することを検証する。</p>
 *
 * <p>本テストは TrackingActivity を {@link InitializeTrackingCommand} で先に初期化し、
 * NOT_RECEIVED → RECEIVED の遷移を End-to-End で確認する（US15 / RECEIVE → RECEIVED マッピング）。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=true",
                "axon.kafka.fetcher.enabled=true",
                "axon.kafka.consumer.event-processor-mode=tracking"
        }
)
@ActiveProfiles("local-h2")
@Testcontainers
@org.junit.jupiter.api.Tag("kafka-integration")
class HandlingActivityRegisteredKafkaIntegrationTest {

    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("axon.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private EventGateway eventGateway;

    @Autowired
    private TrackingSummaryMapper trackingSummaryMapper;

    @Test
    @DisplayName("US15: 荷役 RECEIVE 登録が Kafka 経由で tracking_summary を RECEIVED に更新する")
    void 荷役RECEIVEで状態がRECEIVEDに伝搬する() {
        String trackingNumber = "TRK-HAND00INT1";
        String bookingId = "B-HAND-INT-01";
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 20, 10, 0);

        // 1) TrackingActivity を NOT_RECEIVED で初期化（採番済みの状態を模擬）。
        commandGateway.sendAndWait(new InitializeTrackingCommand(trackingNumber, bookingId));

        // 2) 投影 (tracking_summary) が NOT_RECEIVED で挿入されるまで待機。
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            TrackingSummary summary = trackingSummaryMapper.findByTrackingNumber(trackingNumber);
            assertThat(summary).isNotNull();
            assertThat(summary.getCurrentStatus()).isEqualTo("NOT_RECEIVED");
        });

        // 3) handlingms が発行する想定の shared.HandlingActivityRegisteredEvent (RECEIVE) を Kafka へ流す。
        eventGateway.publish(new HandlingActivityRegisteredEvent(
                "HA-INT-01", trackingNumber, "RECEIVE",
                occurredAt, "JPTYO", null, "H-INT-001",
                null, false));

        // 4) cross-service 受信 → UpdateTransportStatusCommand → 集約 → 投影 の伝搬を待機。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            TrackingSummary summary = trackingSummaryMapper.findByTrackingNumber(trackingNumber);
            assertThat(summary).isNotNull();
            assertThat(summary.getCurrentStatus()).isEqualTo("RECEIVED");
            assertThat(summary.getCurrentUnlocode()).isEqualTo("JPTYO");
        });
    }

    @Test
    @DisplayName("ADR-0011: 未初期化の TrackingActivity への荷役は WARN スキップで例外伝播しない")
    void 未初期化Aggregateへの荷役はスキップされる() {
        String trackingNumber = "TRK-NOINIT0001";
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 20, 10, 0);

        // tracking_summary が存在しない状態で荷役イベントを流す。
        // 受信ハンドラ側で AggregateNotFoundException が起きるが、ADR-0011 ホワイトリストで WARN スキップ。
        eventGateway.publish(new HandlingActivityRegisteredEvent(
                "HA-NOINIT-01", trackingNumber, "RECEIVE",
                occurredAt, "JPTYO", null, "H-NOINIT", null, false));

        // スキップされて投影は更新されないことを Awaitility で 5 秒間「null のまま」を観測する。
        // 通常 Awaitility は条件成立を待つが、ここでは during で「成立しない」ことを継続観測する。
        await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() ->
                        assertThat(trackingSummaryMapper.findByTrackingNumber(trackingNumber)).isNull());
    }
}
