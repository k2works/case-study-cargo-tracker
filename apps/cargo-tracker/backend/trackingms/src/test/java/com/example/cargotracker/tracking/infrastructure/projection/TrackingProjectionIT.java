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
 * 追跡の投影（US14）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>投影がどう見えるか</b>は
 * 判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private TrackingSummaryMapper trackings;

    @Autowired
    private TrackingEventMapper history;

    private static TrackingInitializedEvent initialized(String trackingNumber, String bookingId) {
        return new TrackingInitializedEvent(trackingNumber, bookingId, "JPTYO", "USNYC",
                "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("US14 §3: 追跡を作ると貨物状態が未受領になり、予約から引ける")
    void createsTrackingWithNotReceived() {
        String trackingNumber = "T-P-" + System.nanoTime();
        String bookingId = "b-" + System.nanoTime();

        projection.on(initialized(trackingNumber, bookingId));

        var row = trackings.findByTrackingNumber(trackingNumber);
        assertThat(row.transportStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(row.originUnlocode()).isEqualTo("JPTYO");
        assertThat(row.destinationUnlocode()).isEqualTo("USNYC");
        assertThat(row.cargoType()).isEqualTo("GENERAL");
        assertThat(trackings.findByBooking(bookingId))
                .as("連鎖が通ったかは予約から引いて確かめる")
                .isNotNull();
    }

    @Test
    @DisplayName("US14: 予定の旅程が積む順に残る（IT9 の荷役が予定と実績を照合する）")
    void keepsTheItineraryInOrder() {
        // **落としても集約の検査は緑のまま。** コマンド → イベント → 投影の
        // どこで落ちても分かるように、投影から読み直す。
        String trackingNumber = "T-P-" + System.nanoTime();

        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        assertThat(trackings.findLegs(trackingNumber))
                .extracting(TrackingSummaryMapper.TrackingLegRow::voyageNumber)
                .containsExactly("V-MOL-001", "V-ONE-002");
    }


    // ---- US17 §3 状態の履歴（T4） ----

    private static TransportStatusUpdatedEvent updated(String trackingNumber,
            TransportStatus from, TransportStatus to, Instant occurredAt) {
        return new TransportStatusUpdatedEvent(trackingNumber, from, to,
                StatusUpdateSource.MANUAL, "JPTYO", occurredAt, "tracker-1", AT);
    }

    @Test
    @DisplayName("US17 §3: 更新すると履歴に 1 行残り、一覧の現在値も変わる")
    void recordsHistoryAndUpdatesCurrentStatus() {
        String trackingNumber = "T-H-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        projection.on(updated(trackingNumber, TransportStatus.NOT_RECEIVED,
                TransportStatus.RECEIVED, Instant.parse("2026-09-11T02:00:00Z")), "evt-1");

        // **記録と読み口は対で出す。** 履歴だけ書いて一覧が古いままだと、
        // 同じ画面の中で食い違って見える。
        assertThat(trackings.findByTrackingNumber(trackingNumber).transportStatus())
                .isEqualTo("RECEIVED");

        var rows = history.findHistory(trackingNumber);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).eventType()).isEqualTo("MANUAL");
        assertThat(rows.get(0).previousStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(rows.get(0).newStatus()).isEqualTo("RECEIVED");
        assertThat(rows.get(0).location()).isEqualTo("JPTYO");
        assertThat(rows.get(0).recordedBy()).isEqualTo("tracker-1");
    }

    @Test
    @DisplayName("履歴は起きた順に並ぶ（記録した順ではない）")
    void ordersHistoryByWhenItHappened() {
        String trackingNumber = "T-H-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        // **後から入れた記録のほうが、業務上は先に起きている。**
        projection.on(updated(trackingNumber, TransportStatus.RECEIVED, TransportStatus.LOADED,
                Instant.parse("2026-09-12T02:00:00Z")), "evt-late");
        projection.on(updated(trackingNumber, TransportStatus.NOT_RECEIVED,
                TransportStatus.RECEIVED, Instant.parse("2026-09-11T02:00:00Z")), "evt-early");

        assertThat(history.findHistory(trackingNumber))
                .extracting(TrackingEventMapper.TrackingEventRow::newStatus)
                .containsExactly("RECEIVED", "LOADED");
    }

    @Test
    @DisplayName("追跡が無ければ空の行を作らない（出発地も目的地も無い追跡が一覧に出る）")
    void doesNotCreateAnEmptyTracking() {
        String trackingNumber = "T-H-" + System.nanoTime();

        projection.on(updated(trackingNumber, TransportStatus.NOT_RECEIVED,
                TransportStatus.RECEIVED, Instant.parse("2026-09-11T02:00:00Z")), "evt-orphan");

        assertThat(trackings.findByTrackingNumber(trackingNumber)).isNull();
        assertThat(history.findHistory(trackingNumber))
                .as("履歴そのものは残す。届いた事実を捨てると、後から追えない")
                .hasSize(1);
    }
}
