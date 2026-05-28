package com.example.trackingms.interfaces.events;

import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link TrackingSummaryProjectionEventHandler} のユニットテスト（US14 / IT5 1.4）。
 */
class TrackingSummaryProjectionEventHandlerTest {

    private TrackingSummaryMapper mapper;
    private TrackingSummaryProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(TrackingSummaryMapper.class);
        handler = new TrackingSummaryProjectionEventHandler(mapper);
    }

    @Test
    @DisplayName("US14: TrackingInitializedEvent で tracking_summary に INSERT（NOT_RECEIVED）")
    void 追跡初期化で投影が挿入される() {
        handler.on(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"));

        verify(mapper).insertTrackingSummary(
                eq("TRK-AB12CD3456"), eq("B-001"), eq("NOT_RECEIVED"));
    }
}
