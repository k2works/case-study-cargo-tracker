package com.example.cargotracker.tracking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 状態の手動更新を<b>実 Axon Server から通す</b>（US17 / T3・T4）。
 *
 * <p><b>層が全部緑でも配線は未検査</b>（IT7 の教訓）。投影の検査はハンドラを直に
 * 呼ぶので、{@code @MessageIdentifier} が実際に解決されるか・コマンドが集約へ
 * 届くかを判別しない。ここでしか出ない欠陥がある。</p>
 *
 * <p><b>集約の復元も同じ理由でここにしかない。</b> {@code AxonTestFixture} の
 * {@code disableAxonServer()} ではタグによる復元が働かないため、「例外中は動かさない」の
 * ような状態を見る守りは実 Axon Server でないと固定できない（[ADR-0001] 決定 5 第 8 項）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingStatusUpdateIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private TrackingSummaryMapper trackings;

    @Autowired
    private TrackingEventMapper history;

    @Test
    @DisplayName("US17: 手動更新が集約を通り、履歴と一覧の現在値になる")
    void manualUpdateFlowsThroughToTheProjection() {
        String trackingNumber = "TRK-IT" + System.nanoTime() % 100000000L;
        commandGateway.sendAndWait(new InitializeTrackingCommand(trackingNumber, "b-", "SHP-000001" + System.nanoTime(), "JPTYO", "USNYC", "GENERAL",
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T00:30:00Z")), String.class);

        commandGateway.sendAndWait(new UpdateTransportStatusCommand(trackingNumber,
                TransportStatus.RECEIVED, "JPTYO",
                Instant.parse("2026-09-11T02:00:00Z"), "tracker-1"), Void.class);

        // 投影は非同期に追いつく。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(trackings.findByTrackingNumber(trackingNumber))
                    .isNotNull()
                    .extracting(TrackingSummaryMapper.TrackingSummaryRow::transportStatus)
                    .isEqualTo("RECEIVED");

            var rows = history.findHistory(trackingNumber);
            assertThat(rows).hasSize(1);
            // **採番していないことがここで分かる。** Axon が振った識別子が
            // そのまま主キーになっているので、空でも「1」でもない。
            assertThat(rows.get(0).eventId()).isNotBlank();
            assertThat(rows.get(0).eventType()).isEqualTo("MANUAL");
            assertThat(rows.get(0).previousStatus()).isEqualTo("NOT_RECEIVED");
        });
    }
}
