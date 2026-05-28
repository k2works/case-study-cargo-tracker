package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.AssignRouteToCargoCommand;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.ConfirmBookingCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.CargoSummaryMapper;
import com.example.shared.events.CargoTrackedEvent;
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
 * cross-service 採番完了通知（US14 / ADR-0009 / IT5 1.4）の Kafka 疎通統合テスト
 * （trackingms → bookingms 方向）。
 *
 * <p>Testcontainers Kafka を起動し、trackingms が発行する想定の
 * {@link CargoTrackedEvent} を Event Bus に流すと、KafkaPublisher → cargo-events トピック →
 * StreamableKafkaMessageSource → {@code booking-saga} tracking プロセッサ →
 * {@code BookingSagaManager.@SagaEventHandler} → {@code AssignTrackingDetailsCommand} →
 * Cargo 集約の {@code CargoTrackingAssignedEvent} → {@code CargoProjectionsEventHandler} の
 * 物理経路を通り、{@code cargo_summary} に {@code tracking_number} が設定され
 * {@code booking_status} が TRACKING_ISSUED に更新されることを検証する。</p>
 *
 * <p>本テストは予約を CONFIRMED 状態まで進めた上で、Saga が CargoTrackedEvent を購読して
 * 集約に状態更新コマンドを送信できることを担保する。</p>
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
class CargoTrackedKafkaIntegrationTest {

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

    @Test
    @DisplayName("US14 / IT5 1.4: 採番完了通知が Kafka 経由で予約を TRACKING_ISSUED に更新する")
    void 採番完了通知がKafka経由で予約状態を更新する() {
        String bookingId = "B-CTK-01";

        // 1) 予約を CONFIRMED 状態まで進める（ローカル）。
        commandGateway.sendAndWait(new BookCargoCommand(
                bookingId,
                "S-CTK-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL, new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60), 10, "電子部品")));
        commandGateway.sendAndWait(new RequestRouteDesignCommand(bookingId));
        commandGateway.sendAndWait(new AssignRouteToCargoCommand(bookingId, List.of(
                new Leg("V-CTK", "JPTYO", "USNYC",
                        LocalDateTime.of(2026, 7, 3, 9, 0),
                        LocalDateTime.of(2026, 7, 28, 18, 0)))));
        commandGateway.sendAndWait(new ConfirmBookingCommand(bookingId));

        // 2) trackingms が発行する想定の CargoTrackedEvent を Kafka へ流す。
        eventGateway.publish(new CargoTrackedEvent(bookingId, "TRK-INT0123456"));

        // 3) Saga → AssignTrackingDetailsCommand → 集約 → 投影 までを待機。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            CargoSummary summary = cargoSummaryMapper.findByBookingId(bookingId);
            assertThat(summary).isNotNull();
            assertThat(summary.getBookingStatus()).isEqualTo("TRACKING_ISSUED");
            assertThat(summary.getTrackingNumber()).isEqualTo("TRK-INT0123456");
        });
    }
}
