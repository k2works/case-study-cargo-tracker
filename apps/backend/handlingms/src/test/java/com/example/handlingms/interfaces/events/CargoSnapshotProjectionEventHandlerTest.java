package com.example.handlingms.interfaces.events;

import com.example.handlingms.infrastructure.repositories.mybatis.CargoSnapshotMapper;
import com.example.shared.events.CargoTrackedEvent;
import com.example.shared.events.TrackingIssuanceRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CargoSnapshotProjectionEventHandler} のユニットテスト（IT5 3.1）。
 */
class CargoSnapshotProjectionEventHandlerTest {

    private CargoSnapshotMapper mapper;
    private CargoSnapshotProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(CargoSnapshotMapper.class);
        handler = new CargoSnapshotProjectionEventHandler(mapper);
    }

    @Test
    @DisplayName("US15: TrackingIssuanceRequestedEvent で cargo_snapshot に upsert される")
    void upsertが呼ばれる() {
        handler.on(new TrackingIssuanceRequestedEvent(
                "B-001", "JPTYO", "USNYC",
                LocalDate.of(2026, 9, 30), "GENERAL",
                List.of()));

        verify(mapper).upsert(eq("B-001"), eq("JPTYO"), eq("USNYC"), eq("GENERAL"));
    }

    @Test
    @DisplayName("US15: CargoTrackedEvent で tracking_number が更新される")
    void trackingNumberが更新される() {
        when(mapper.updateTrackingNumber(eq("B-001"), eq("TRK-AB12CD3456"))).thenReturn(1);

        handler.on(new CargoTrackedEvent("B-001", "TRK-AB12CD3456"));

        verify(mapper).updateTrackingNumber(eq("B-001"), eq("TRK-AB12CD3456"));
    }

    @Test
    @DisplayName("snapshot 未到着でも例外を投げずに WARN スキップする")
    void snapshot未到着でスキップ() {
        when(mapper.updateTrackingNumber(eq("B-999"), eq("TRK-XX12CD3456"))).thenReturn(0);

        // 例外伝播せず、updateTrackingNumber が呼ばれるだけ
        handler.on(new CargoTrackedEvent("B-999", "TRK-XX12CD3456"));

        verify(mapper).updateTrackingNumber(eq("B-999"), eq("TRK-XX12CD3456"));
    }
}
