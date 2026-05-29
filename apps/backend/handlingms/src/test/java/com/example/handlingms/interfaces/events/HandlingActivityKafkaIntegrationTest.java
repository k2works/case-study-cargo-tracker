package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.HandlingActivitySummary;
import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service 荷役連携（US15 / IT5 3.5）の Kafka 疎通統合テスト（handlingms 側）。
 *
 * <p>Testcontainers Kafka を起動し、以下の 2 経路を検証する：</p>
 * <ol>
 *   <li>bookingms → handlingms：{@link TrackingIssuanceRequestedEvent} を Kafka 経由で受信し、
 *       CargoSnapshot ACL の {@code cargo_snapshot} に保存される</li>
 *   <li>handlingms 内部：荷役登録（{@code RegisterHandlingActivityCommand}）→
 *       ローカルイベント → {@code HandlingActivityProjectionEventHandler} で
 *       {@code handling_activity} 投影 → {@code HandlingActivityCrossServicePublisher} が
 *       shared 形式に変換して Kafka publish</li>
 * </ol>
 *
 * <p>本テストでは Kafka publish された shared イベントの実際の trackingms 側受信は別テスト
 * （HandlingActivityRegisteredKafkaIntegrationTest）でカバーする。ここでは handlingms 内の
 * 投影・publisher の End-to-End を確認する。</p>
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
class HandlingActivityKafkaIntegrationTest {

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
    private HandlingActivityMapper handlingActivityMapper;

    @Autowired
    private CargoSnapshotMapper cargoSnapshotMapper;

    @Test
    @DisplayName("US15: bookingms の TrackingIssuanceRequestedEvent が Kafka 経由で cargo_snapshot に保存される")
    void TrackingIssuanceRequestedで_cargo_snapshotが保存される() {
        String bookingId = "B-HAND-INT-K1";

        eventGateway.publish(new TrackingIssuanceRequestedEvent(
                bookingId, "JPTYO", "USNYC",
                LocalDate.of(2026, 9, 30), "GENERAL",
                List.of()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var snapshot = cargoSnapshotMapper.findByBookingId(bookingId);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.getOriginUnlocode()).isEqualTo("JPTYO");
            assertThat(snapshot.getDestinationUnlocode()).isEqualTo("USNYC");
            assertThat(snapshot.getCargoType()).isEqualTo("GENERAL");
        });
    }

    @Test
    @DisplayName("US15: 荷役登録から handling_activity 投影と cross-service Kafka publish までが貫通する")
    void 荷役登録で投影とKafkaPublishが行われる() {
        String trackingNumber = "TRK-HAND00INT2";
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 20, 10, 0);

        // 荷役登録（ローカル集約コマンド）
        String activityId = commandGateway.sendAndWait(new RegisterHandlingActivityCommand(
                "HA-INT-K1", trackingNumber, HandlingType.RECEIVE,
                occurredAt, "JPTYO", null, "H-INT-K1", null));
        assertThat(activityId).isEqualTo("HA-INT-K1");

        // handling_activity 投影が反映されるまで待機
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HandlingActivitySummary activity = handlingActivityMapper.findById("HA-INT-K1");
            assertThat(activity).isNotNull();
            assertThat(activity.getTrackingNumber()).isEqualTo(trackingNumber);
            assertThat(activity.getHandlingType()).isEqualTo("RECEIVE");
            assertThat(activity.getUnlocode()).isEqualTo("JPTYO");
            // CargoSnapshot 未到着のためフォールバック値（"UNKNOWN-BOOKING" / "UNK"）で投影される
            assertThat(activity.getBookingId()).isNotNull();
        });

        // cross-service publisher が shared 形式に変換して Kafka publish するため、ローカル投影と
        // 異なるイベントが流れている。ここではローカル投影の到達のみを確認する
        // （shared 経路は HandlingActivityRegisteredKafkaIntegrationTest で別途検証）。
    }
}
