package com.example.routingms.interfaces.events;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import com.example.shared.events.RouteDesignRequestedEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service イベント連携（ADR-0009、T7）の Kafka 疎通統合テスト。
 *
 * <p>Testcontainers Kafka を起動し、{@code RouteDesignRequestedEvent} を発行すると
 * KafkaPublisher → cargo-events トピック → StreamableKafkaMessageSource →
 * {@code route-design-requests} tracking プロセッサ → {@link RouteDesignRequestEventHandler} の物理経路を通り、
 * 経路設計待ちリスト（route_design_request）に記録されることを検証する。
 * subscribing モードのサービス内処理ではなく、Kafka を実際に経由する点が IT2 との差分。</p>
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
class RouteDesignRequestKafkaIntegrationTest {

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
    private RouteDesignRequestMapper mapper;

    @Test
    @DisplayName("ADR-0009: cross-service の経路設計依頼が Kafka 経由で route_design_request に記録される")
    void 経路設計依頼がKafka経由で記録される() {
        eventGateway.publish(new RouteDesignRequestedEvent(
                "B-K01", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(mapper.findByBookingId("B-K01")).isNotNull());

        RouteDesignRequestProjection found = mapper.findByBookingId("B-K01");
        assertThat(found.getOriginUnlocode()).isEqualTo("JPTYO");
        assertThat(found.getDestinationUnlocode()).isEqualTo("USNYC");
        assertThat(found.getCargoType()).isEqualTo("GENERAL");
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }
}
