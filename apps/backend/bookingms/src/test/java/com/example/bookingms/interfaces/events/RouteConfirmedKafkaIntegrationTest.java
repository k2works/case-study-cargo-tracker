package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.projections.CargoLeg;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoLegMapper;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import com.example.shared.events.RouteConfirmedEvent;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service 経路確定連携（US11 / ADR-0009）の Kafka 疎通統合テスト（routingms → bookingms 方向）。
 *
 * <p>Testcontainers Kafka を起動し、routingms が発行する想定の {@link RouteConfirmedEvent} を Event Bus へ流すと、
 * KafkaPublisher → cargo-events トピック → StreamableKafkaMessageSource → {@code route-confirmed} tracking
 * プロセッサ → {@link RouteConfirmedEventHandler} → {@code AssignRouteToCargoCommand} → Cargo の
 * {@code CargoRoutedEvent} → {@code CargoProjectionsEventHandler} の物理経路を通り、予約状態が ROUTE_PROPOSED へ更新され
 * cargo_leg が確定することを検証する。</p>
 *
 * <p>本テストは bookingms を Kafka 有効（fetcher + consumer tracking）で起動するため、route-confirmed tracking
 * プロセッサの source 配線（StreamableKafkaMessageSource）も同時に検証する。</p>
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
class RouteConfirmedKafkaIntegrationTest {

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
    private CargoSummaryMapper cargoSummaryMapper;

    @Autowired
    private CargoLegMapper cargoLegMapper;

    @Test
    @DisplayName("US11: cross-service の経路確定が Kafka 経由で予約状態を ROUTE_PROPOSED に更新する")
    void 経路確定がKafka経由で予約に紐付く() {
        String bookingId = "B-RCK01";
        // 1) 予約登録 → 経路設計引き渡し（ROUTING）まで進める（bookingms ローカル）。
        commandGateway.sendAndWait(new BookCargoCommand(
                bookingId,
                "S-RCK-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL, new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60), 10, "電子部品")));
        commandGateway.sendAndWait(new RequestRouteDesignCommand(bookingId));

        // 2) routingms が発行する想定の RouteConfirmedEvent を Kafka へ流す。
        eventGateway.publish(new RouteConfirmedEvent(bookingId, List.of(
                new RouteConfirmedEvent.LegData("V-RCK", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0)))));

        // 3) cross-service 受信 → 経路割当 → 投影更新を待つ。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            CargoSummary summary = cargoSummaryMapper.findByBookingId(bookingId);
            assertThat(summary).isNotNull();
            assertThat(summary.getBookingStatus()).isEqualTo("ROUTE_PROPOSED");
            assertThat(summary.getRoutingStatus()).isEqualTo("ROUTED");
        });

        List<CargoLeg> legs = cargoLegMapper.findByBookingId(bookingId);
        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).getVoyageNumber()).isEqualTo("V-RCK");
        assertThat(legs.get(0).getLoadUnlocode()).isEqualTo("JPTYO");
        assertThat(legs.get(0).getUnloadUnlocode()).isEqualTo("USNYC");
    }
}
