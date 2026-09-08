package com.example.cargotracker.tracking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.domain.model.events.ExceptionResponseStartedEvent;
import com.example.cargotracker.tracking.domain.model.events.HandlingNotAppliedEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionRegisteredEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionResolvedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingExceptionMapper;
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

    @Autowired
    private TrackingExceptionMapper exceptions;

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
                StatusUpdateSource.MANUAL, null, "JPTYO", occurredAt, "tracker-1", AT);
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

    // ---- US19 例外の投影（IT10 T5） ----

    private static final Instant OCCURRED = Instant.parse("2026-09-20T02:00:00Z");

    private static TrackingExceptionRegisteredEvent exceptionRegistered(String trackingNumber,
            String exceptionId, ExceptionType type) {
        return new TrackingExceptionRegisteredEvent(trackingNumber, exceptionId, type.name(),
                OCCURRED, "SGSIN", "台風で 3 日遅れます", type.urgent(),
                TransportStatus.RECEIVED, "tracker01", AT);
    }

    @Test
    @DisplayName("US19 §5: 起票が例外一覧に出て、件数が追跡に写る")
    void writesTheException() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        projection.on(exceptionRegistered(trackingNumber, "ex-1", ExceptionType.DELAY),
                "evt-ex-1");

        var row = exceptions.findById("ex-1");
        assertThat(row).isNotNull();
        assertThat(row.exceptionType()).isEqualTo("DELAY");
        assertThat(row.responseStatus()).isEqualTo("REPORTED");
        // **判定は写す**（不変条件 7）。投影に書き直さない。
        assertThat(row.urgent()).isFalse();

        var summary = trackings.findByTrackingNumber(trackingNumber);
        assertThat(summary.openExceptionCount())
                .as("一覧が tracking_exception を数えないための写し").isEqualTo(1);
        assertThat(summary.urgentExceptionCount()).isZero();
    }

    @Test
    @DisplayName("不変条件 7: 紛失は緊急として写る（件数も緊急で数える）")
    void countsUrgentExceptions() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        projection.on(exceptionRegistered(trackingNumber, "ex-2", ExceptionType.LOSS),
                "evt-ex-2");

        assertThat(exceptions.findById("ex-2").urgent()).isTrue();
        assertThat(trackings.findByTrackingNumber(trackingNumber).urgentExceptionCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("US19 §5: 対応開始と解決が例外に残り、解決すると未解決の件数が減る")
    void writesTheResponseAndResolution() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));
        projection.on(exceptionRegistered(trackingNumber, "ex-3", ExceptionType.DELAY),
                "evt-ex-3");

        projection.on(new ExceptionResponseStartedEvent(trackingNumber, "ex-3",
                "2026-09-27", "代替便を手配中", "tracker01", AT));
        assertThat(exceptions.findById("ex-3").responseStatus()).isEqualTo("RESPONDING");

        projection.on(new TrackingExceptionResolvedEvent(trackingNumber, "ex-3",
                "代替便に振り替えました", "tracker01", AT), "evt-ex-3-x");

        var row = exceptions.findById("ex-3");
        assertThat(row.responseStatus()).isEqualTo("RESOLVED");
        // **解決しても消えない**（不変条件 6）。料金調整の根拠として残る。
        assertThat(row.resolution()).isEqualTo("代替便に振り替えました");
        assertThat(row.description()).isEqualTo("台風で 3 日遅れます");
        assertThat(trackings.findByTrackingNumber(trackingNumber).openExceptionCount()).isZero();
    }

    @Test
    @DisplayName("履歴に例外の起票と解決が別の種別で残る（逆向きの出来事を同じ印にしない）")
    void writesExceptionHistory() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));
        projection.on(exceptionRegistered(trackingNumber, "ex-4", ExceptionType.DELAY),
                "evt-ex-4");
        projection.on(new TrackingExceptionResolvedEvent(trackingNumber, "ex-4",
                "対応済み", "tracker01", AT), "evt-ex-4-x");

        assertThat(history.findHistory(trackingNumber))
                .extracting(TrackingEventMapper.TrackingEventRow::eventType)
                .contains("EXCEPTION", "RESOLVED");
    }

    @Test
    @DisplayName("追記系はリプレイで行を増やさない（同じ例外を二度読んでも 1 行）")
    void doesNotDuplicateOnReplay() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        projection.on(exceptionRegistered(trackingNumber, "ex-5", ExceptionType.DELAY),
                "evt-ex-5");
        projection.on(exceptionRegistered(trackingNumber, "ex-5", ExceptionType.DELAY),
                "evt-ex-5");

        assertThat(exceptions.findOpen()).filteredOn(row -> "ex-5".equals(row.exceptionId()))
                .hasSize(1);
        assertThat(trackings.findByTrackingNumber(trackingNumber).openExceptionCount())
                .as("件数も二度数えない").isEqualTo(1);
    }

    @Test
    @DisplayName("M6: 反映できなかった荷役が履歴に残る（無言で捨てない）")
    void writesHandlingThatCouldNotAdvance() {
        String trackingNumber = "T-E-" + System.nanoTime();
        projection.on(initialized(trackingNumber, "b-" + System.nanoTime()));

        projection.on(new HandlingNotAppliedEvent(trackingNumber, "act-9", "CLAIM", "JPTYO",
                TransportStatus.NOT_RECEIVED, TransportStatus.DELIVERED, OCCURRED, AT),
                "evt-na-1");

        assertThat(history.findHistory(trackingNumber))
                .extracting(TrackingEventMapper.TrackingEventRow::eventType)
                .contains("NOT_APPLIED");
        assertThat(trackings.findByTrackingNumber(trackingNumber).transportStatus())
                .as("状態は動かさない").isEqualTo("NOT_RECEIVED");
    }

    @Test
    @DisplayName("US18: 荷主で絞って自社の追跡だけを引ける")
    void findsTrackingsByShipper() {
        // **値は全層を生き延びるか確かめる。** shipperId は
        // TrackingNumberIssuedEvent → InitializeTrackingCommand → TrackingInitializedEvent
        // の 3 本を通って来る。1 本でも落とすとここで空になる。
        String mine = "T-S-" + System.nanoTime();
        String other = "T-S-" + System.nanoTime();
        projection.on(initialized(mine, "b-" + System.nanoTime()));
        projection.on(new TrackingInitializedEvent(other, "b-" + System.nanoTime(),
                "SHP-000999", "JPTYO", "USNYC", "GENERAL", List.of(), AT));

        assertThat(trackings.findAll("SHP-000001", true, 50))
                .extracting(TrackingSummaryMapper.TrackingSummaryRow::trackingNumber)
                .contains(mine)
                .as("他社の追跡が混ざると、荷主に他人の貨物が見える")
                .doesNotContain(other);
    }
}
