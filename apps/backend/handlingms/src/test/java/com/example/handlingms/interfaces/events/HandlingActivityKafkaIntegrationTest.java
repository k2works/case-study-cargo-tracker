package com.example.handlingms.interfaces.events;

import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * cross-service 荷役連携 入力経路（US15 / IT5 3.5）の Kafka 疎通統合テスト
 * （bookingms → handlingms の CargoSnapshot ACL）。
 *
 * <p>IT5 レビュー H7 対応: 「Kafka を起動するのに publish の verify をしていない」という
 * tester 指摘を踏まえて、本クラスは <strong>cross-service の受信側経路のみ</strong>を
 * 検証する。送出側経路（荷役登録 → cross-service publish）は trackingms 側の
 * {@code HandlingActivityRegisteredKafkaIntegrationTest} で受信側として End-to-End 検証済み。
 * 送出された Kafka record を直接 verify する責任は本テストの対象外。
 * ローカル投影単体は Kafka 不要なテスト
 * （{@code HandlingActivityProjectionIntegrationTest}）に分離する。</p>
 *
 * <p>Testcontainers Kafka を起動し、bookingms が発行する想定の
 * {@link TrackingIssuanceRequestedEvent} を Kafka に流すと、cargo-snapshot tracking processor →
 * {@code CargoSnapshotProjectionEventHandler} → {@code cargo_snapshot} テーブルに
 * origin / destination / cargoType が冪等に保存されることを検証する。</p>
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
// IT5 既知事象（Kafka container race）の分離：check タスクから除外し
// gradle :handlingms:kafkaTest で個別実行可能にする。IT6 で構造的解決予定。
@org.junit.jupiter.api.Tag("kafka-integration")
class HandlingActivityKafkaIntegrationTest {

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
}
