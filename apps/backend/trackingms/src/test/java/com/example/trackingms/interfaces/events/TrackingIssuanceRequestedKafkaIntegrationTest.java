package com.example.trackingms.interfaces.events;

import com.example.shared.events.TrackingIssuanceRequestedEvent;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service 追跡発行依頼（US14 / ADR-0009 / ADR-0011）の Kafka 疎通統合テスト
 * （bookingms → trackingms 方向）。
 *
 * <p>Testcontainers Kafka を起動し、bookingms が発行する想定の
 * {@link TrackingIssuanceRequestedEvent} を Event Bus に流すと、
 * KafkaPublisher → cargo-events トピック → StreamableKafkaMessageSource →
 * {@code tracking-issuance-requests} tracking プロセッサ →
 * {@code TrackingIssuanceRequestedEventHandler} → {@code TrackingNumberGenerator} で採番 →
 * {@code InitializeTrackingCommand} → {@code TrackingActivity} 集約の
 * {@code TrackingInitializedEvent} → {@code TrackingSummaryProjectionEventHandler} の物理経路を通り、
 * {@code tracking_summary} に新規 1 行が NOT_RECEIVED で挿入されることを検証する。</p>
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
class TrackingIssuanceRequestedKafkaIntegrationTest {

    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("axon.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private EventGateway eventGateway;

    @Autowired
    private TrackingSummaryMapper trackingSummaryMapper;

    @Test
    @DisplayName("US14: cross-service の追跡発行依頼が Kafka 経由で tracking_summary に NOT_RECEIVED 投影される")
    void 追跡発行依頼がKafka経由で投影更新する() {
        String bookingId = "B-TRK-INT-01";

        // bookingms が発行する想定の TrackingIssuanceRequestedEvent を Kafka へ流す。
        eventGateway.publish(new TrackingIssuanceRequestedEvent(
                bookingId,
                "JPTYO", "USNYC",
                LocalDate.of(2026, 9, 30),
                "GENERAL",
                List.of(new TrackingIssuanceRequestedEvent.LegData(
                        "V-INT-01", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0),
                        LocalDateTime.of(2026, 7, 28, 18, 0)))
        ));

        // cross-service 受信 → InitializeTrackingCommand → TrackingActivity 集約初期化 →
        // TrackingInitializedEvent → tracking_summary 投影 までを待機。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            TrackingSummary summary = trackingSummaryMapper.findByBookingId(bookingId);
            assertThat(summary).isNotNull();
            assertThat(summary.getBookingId()).isEqualTo(bookingId);
            assertThat(summary.getCurrentStatus()).isEqualTo("NOT_RECEIVED");
            assertThat(summary.isMisrouted()).isFalse();
            // 採番は TrackingNumberGenerator（SecureRandom）のため値は固定できない。
            // 書式（TRK- + 大文字英数 10 桁）のみ検証する。
            assertThat(summary.getTrackingNumber())
                    .isNotNull()
                    .matches("^TRK-[A-Z0-9]{10}$");
        });
    }
}
