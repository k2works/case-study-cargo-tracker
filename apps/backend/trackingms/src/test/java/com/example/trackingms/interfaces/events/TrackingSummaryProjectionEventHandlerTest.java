package com.example.trackingms.interfaces.events;

import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingEventMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link TrackingSummaryProjectionEventHandler} のユニットテスト（US14 / US17 / IT5 1.4 + 2.3）。
 */
class TrackingSummaryProjectionEventHandlerTest {

    private TrackingSummaryMapper summaryMapper;
    private TrackingEventMapper eventMapper;
    private TrackingSummaryProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        summaryMapper = mock(TrackingSummaryMapper.class);
        eventMapper = mock(TrackingEventMapper.class);
        handler = new TrackingSummaryProjectionEventHandler(summaryMapper, eventMapper);
    }

    @Test
    @DisplayName("US14: TrackingInitializedEvent で tracking_summary に INSERT（NOT_RECEIVED）")
    void 追跡初期化で投影が挿入される() {
        handler.on(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"));

        verify(summaryMapper).insertTrackingSummary(
                eq("TRK-AB12CD3456"), eq("B-001"), eq("NOT_RECEIVED"));
        verify(eventMapper).insertTrackingEvent(
                eq("TRK-AB12CD3456"), any(LocalDateTime.class),
                eq("TRACKING_INITIALIZED"), eq("NOT_RECEIVED"),
                eq(null), eq(null), eq(null), eq("SYSTEM"), eq(null));
    }

    @Test
    @DisplayName("US17: TransportStatusUpdatedEvent で tracking_summary を更新し tracking_event に MANUAL 履歴を追加")
    void 状態更新で投影と履歴が更新される() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        TransportStatusUpdatedEvent event = new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                "JPTYO", null, occurredAt, "貨物を東京港で受領");

        handler.on(event);

        verify(summaryMapper).updateStatus(
                eq("TRK-AB12CD3456"), eq("RECEIVED"),
                eq("JPTYO"), eq(null), eq(occurredAt));
        verify(eventMapper).insertTrackingEvent(
                eq("TRK-AB12CD3456"), eq(occurredAt),
                eq("STATUS_UPDATED"), eq("RECEIVED"),
                eq("JPTYO"), eq(null), eq(null),
                eq("MANUAL"), eq("貨物を東京港で受領"));
    }

    @Test
    @DisplayName("US17: CargoMisroutedEvent で tracking_summary.misrouted = true")
    void 誤配送検知で投影のフラグが立つ() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 22, 12, 0);
        handler.on(new CargoMisroutedEvent("TRK-AB12CD3456", "CNHKG", occurredAt));

        verify(summaryMapper).markMisrouted(eq("TRK-AB12CD3456"), eq(occurredAt));
    }
}
