package com.example.cargotracker.tracking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 投影のリプレイ（[ADR-0001] コンプライアンス「投影がコマンドを送らない」）。
 *
 * <p>ArchUnit はコンパイル時の依存しか見ておらず、<b>実行時に呼ばれないことの保証では
 * ない</b>。ここでは投影のハンドラをもう一度流し、副作用が積み上がらないことを確かめる。</p>
 *
 * <p><b>「行が増えない」だけでは足りない。</b> 追跡そのものは主キーで上書きになるが、
 * <b>旅程は追記の表</b>である。消してから入れ直さないと、リプレイのたびに区間が倍に
 * なり、荷役（IT9）が予定と実績を照合できなくなる（IT6 の「追記専用の行はリプレイで
 * 増える」）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplayIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingSummaryMapper trackings;

    @Autowired
    private TrackingEventMapper history;

    private static TrackingInitializedEvent initialized(String trackingNumber, String bookingId) {
        return new TrackingInitializedEvent(trackingNumber, bookingId, "SHP-000001",
                "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("追跡開始を 2 度読んでも追跡は 1 つで、区間も倍にならない")
    void replayingInitializationDoesNotDuplicate() {
        String trackingNumber = "T-R-" + System.nanoTime();
        String bookingId = "b-" + System.nanoTime();

        projection.on(initialized(trackingNumber, bookingId));
        projection.on(initialized(trackingNumber, bookingId));

        assertThat(trackings.findByTrackingNumber(trackingNumber)).isNotNull();
        assertThat(trackings.findLegs(trackingNumber))
                .as("追記だけにすると、リプレイで区間が倍になる")
                .hasSize(2);
    }

    @Test
    @DisplayName("状態更新を 2 度読んでも履歴は 1 行のまま（追記の表は主キーで守る）")
    void replayingStatusUpdateDoesNotDuplicateHistory() {
        // **投影テーブルが冪等でも、受け皿は対象外**（IT6 の「追記専用の行は
        // リプレイで増える」）。履歴は追記なので、主キーが元イベントの識別子で
        // ないかぎり読み直すたびに積み上がる。
        String trackingNumber = "T-R-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        var event = new TransportStatusUpdatedEvent(trackingNumber,
                TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                StatusUpdateSource.MANUAL, "JPTYO",
                Instant.parse("2026-09-11T02:00:00Z"), "tracker-1", AT);

        projection.on(event, "evt-replay");
        projection.on(event, "evt-replay");

        assertThat(history.findHistory(trackingNumber)).hasSize(1);
    }
}
