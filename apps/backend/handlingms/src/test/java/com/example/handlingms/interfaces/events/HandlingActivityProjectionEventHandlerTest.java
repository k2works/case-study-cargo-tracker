package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.CargoSnapshot;
import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HandlingActivityProjectionEventHandler} のユニットテスト（IT5 3.x）。
 */
class HandlingActivityProjectionEventHandlerTest {

    private HandlingActivityMapper activityMapper;
    private CargoSnapshotMapper snapshotMapper;
    private SimpleMeterRegistry meterRegistry;
    private HandlingActivityProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        activityMapper = mock(HandlingActivityMapper.class);
        snapshotMapper = mock(CargoSnapshotMapper.class);
        meterRegistry = new SimpleMeterRegistry();
        handler = new HandlingActivityProjectionEventHandler(activityMapper, snapshotMapper, meterRegistry);
    }

    private CargoSnapshot snapshot() {
        CargoSnapshot s = new CargoSnapshot();
        s.setBookingId("B-001");
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setOriginUnlocode("JPTYO");
        s.setDestinationUnlocode("USNYC");
        s.setCargoType("GENERAL");
        return s;
    }

    @Test
    @DisplayName("US15: CargoSnapshot から booking_id 等を補完して INSERT される")
    void snapshotで補完されINSERTされる() {
        when(snapshotMapper.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(snapshot());
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);

        handler.on(new HandlingActivityRegisteredEvent(
                "A-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                occurredAt, "JPTYO", null, "H-001", null));

        verify(activityMapper).insert(
                eq("A-001"), eq("B-001"), eq("TRK-AB12CD3456"),
                eq("JPTYO"), eq("USNYC"), eq("GENERAL"),
                eq("RECEIVE"), eq(occurredAt),
                eq("JPTYO"), eq(null), eq("H-001"), eq(false));
    }

    @Test
    @DisplayName("snapshot 未到着時はダミー値で INSERT を続行する")
    void snapshot未到着でも処理を続行する() {
        when(snapshotMapper.findByTrackingNumber("TRK-NO0SNAP3456")).thenReturn(null);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);

        handler.on(new HandlingActivityRegisteredEvent(
                "A-002", "TRK-NO0SNAP3456", HandlingType.LOAD,
                occurredAt, "JPTYO", "V-220", "H-001", null));

        verify(activityMapper).insert(
                eq("A-002"), eq("UNKNOWN-BOOKING"), eq("TRK-NO0SNAP3456"),
                eq("UNK"), eq("UNK"), eq("UNKNOWN"),
                eq("LOAD"), eq(occurredAt),
                eq("JPTYO"), eq("V-220"), eq("H-001"), eq(false));
    }

    @Test
    @DisplayName("H2: snapshot 未到着時に handlingms.projection.snapshot_missing カウンターが増える")
    void snapshot未到着でカウンターが増える() {
        when(snapshotMapper.findByTrackingNumber("TRK-NO0SNAP3456")).thenReturn(null);

        // 2 回のフォールバックを発生させる
        handler.on(new HandlingActivityRegisteredEvent(
                "A-001", "TRK-NO0SNAP3456", HandlingType.RECEIVE,
                LocalDateTime.of(2026, 3, 1, 10, 0), "JPTYO", null, "H-001", null));
        handler.on(new HandlingActivityRegisteredEvent(
                "A-002", "TRK-NO0SNAP3456", HandlingType.LOAD,
                LocalDateTime.of(2026, 3, 2, 10, 0), "JPTYO", "V-220", "H-001", null));

        // カウンターが 2 になる
        assertThat(meterRegistry.find(
                HandlingActivityProjectionEventHandler.METRIC_SNAPSHOT_MISSING)
                .counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("H2: snapshot がある場合はカウンターが増えない")
    void snapshotありでカウンターは増えない() {
        when(snapshotMapper.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(snapshot());

        handler.on(new HandlingActivityRegisteredEvent(
                "A-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                LocalDateTime.of(2026, 3, 1, 10, 0), "JPTYO", null, "H-001", null));

        assertThat(meterRegistry.find(
                HandlingActivityProjectionEventHandler.METRIC_SNAPSHOT_MISSING)
                .counter().count()).isEqualTo(0.0);
    }
}
