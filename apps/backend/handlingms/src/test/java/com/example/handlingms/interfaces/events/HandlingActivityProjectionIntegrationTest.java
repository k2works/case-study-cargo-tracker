package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.HandlingActivitySummary;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 荷役登録から handling_activity 投影までのローカル統合テスト（US15 / IT5 3.5 / レビュー H7）。
 *
 * <p>IT5 レビュー H7 対応の分割テスト：tester から「Kafka を起動するのに publish の verify を
 * していない」と指摘された前テストから、<strong>ローカル投影到達検証のみ</strong>を分離した
 * 軽量統合テスト。Testcontainers Kafka を起動せず、{@code axon.kafka.*.enabled=false} で
 * Kafka 関連 Bean を無効化することで、テスト実行時間と flaky リスクを最小化する。</p>
 *
 * <p>cross-service publish の受信側経路は trackingms 側の
 * {@code HandlingActivityRegisteredKafkaIntegrationTest} で End-to-End 検証済み。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        }
)
@ActiveProfiles("local-h2")
class HandlingActivityProjectionIntegrationTest {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private HandlingActivityMapper handlingActivityMapper;

    @Test
    @DisplayName("US15: 荷役登録から handling_activity 投影までが貫通する（CargoSnapshot 未到着でフォールバック）")
    void 荷役登録で投影が反映される() {
        String trackingNumber = "TRK-PROJ00INT1";
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 20, 10, 0);

        String activityId = commandGateway.sendAndWait(new RegisterHandlingActivityCommand(
                "HA-PROJ-1", trackingNumber, HandlingType.RECEIVE,
                occurredAt, "JPTYO", null, "H-PROJ-1", null));
        assertThat(activityId).isEqualTo("HA-PROJ-1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            HandlingActivitySummary activity = handlingActivityMapper.findById("HA-PROJ-1");
            assertThat(activity).isNotNull();
            assertThat(activity.getTrackingNumber()).isEqualTo(trackingNumber);
            assertThat(activity.getHandlingType()).isEqualTo("RECEIVE");
            assertThat(activity.getUnlocode()).isEqualTo("JPTYO");
            // CargoSnapshot 未到着のためフォールバック値（"UNKNOWN-BOOKING" / "UNK"）で投影される
            assertThat(activity.getBookingId()).isEqualTo("UNKNOWN-BOOKING");
            assertThat(activity.getOriginUnlocode()).isEqualTo("UNK");
            assertThat(activity.getCargoType()).isEqualTo("UNKNOWN");
        });
    }
}
