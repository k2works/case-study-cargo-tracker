package com.example.cargotracker.handlingms.application.eventhandlers;

import com.example.cargotracker.handlingms.domain.model.events.HandlingActivityRegisteredEvent;
import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.ClaimVerification;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.ClaimVerificationMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.ClaimVerificationRecord;
import com.example.cargotracker.handlingms.infrastructure.persistence.HandlingActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HandlingProjectionsEventHandler の単体テスト（US15 / US16）。
 *
 * <p>主に US16 受入条件 3/4 の「引取記録で貨物状態が DELIVERED に遷移する」ことを検証する。</p>
 */
@DisplayName("HandlingProjectionsEventHandler")
class HandlingProjectionsEventHandlerTest {

    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260810-DELIVERY1");
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location HAMBURG = Location.of("DEHAM");

    private HandlingActivityMapper handlingActivityMapper;
    private ClaimVerificationMapper claimVerificationMapper;
    private CargoSnapshotMapper cargoSnapshotMapper;
    private HandlingProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        handlingActivityMapper = mock(HandlingActivityMapper.class);
        claimVerificationMapper = mock(ClaimVerificationMapper.class);
        cargoSnapshotMapper = mock(CargoSnapshotMapper.class);
        handler = new HandlingProjectionsEventHandler(
                handlingActivityMapper,
                claimVerificationMapper,
                cargoSnapshotMapper);
    }

    private CargoSnapshot snapshot() {
        return new CargoSnapshot(
                "B-2026-0810-001",
                TRK,
                TOKYO,
                HAMBURG,
                "GENERAL");
    }

    @Test
    @DisplayName("US16 受入3/4: CLAIM 種別を受信すると claim_verification 保存 + 貨物状態 DELIVERED 遷移")
    void CLAIMでDELIVERED遷移() {
        var event = new HandlingActivityRegisteredEvent(
                "ACT-001",
                TRK,
                HandlingType.CLAIM,
                HAMBURG,
                LocalDateTime.of(2026, 8, 10, 14, 30),
                null,
                new HandlerId("handler-002"),
                new ClaimVerification(
                        "John Doe", null, "AX9-2K7",
                        LocalDateTime.of(2026, 8, 10, 14, 30)),
                snapshot(),
                false);

        handler.on(event);

        // handling_activity 投影
        verify(handlingActivityMapper).insert(org.mockito.ArgumentMatchers.any());

        // claim_verification 投影
        ArgumentCaptor<ClaimVerificationRecord> claimCaptor = ArgumentCaptor.forClass(ClaimVerificationRecord.class);
        verify(claimVerificationMapper).insert(claimCaptor.capture());
        var saved = claimCaptor.getValue();
        assertThat(saved.getActivityId()).isEqualTo("ACT-001");
        assertThat(saved.getConsigneeName()).isEqualTo("John Doe");
        assertThat(saved.getConfirmationCode()).isEqualTo("AX9-2K7");

        // cargo_snapshot.booking_status を DELIVERED に更新（US16 受入3）
        verify(cargoSnapshotMapper).updateBookingStatusByTrackingNumber(
                eq("TRK-20260810-DELIVERY1"), eq("DELIVERED"));
    }

    @Test
    @DisplayName("US15: RECEIVE / LOAD など CLAIM 以外では DELIVERED 遷移しない")
    void NonClaimではDELIVERED遷移しない() {
        var event = new HandlingActivityRegisteredEvent(
                "ACT-002",
                TRK,
                HandlingType.RECEIVE,
                TOKYO,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                new HandlerId("handler-001"),
                null,
                snapshot(),
                false);

        handler.on(event);

        verify(handlingActivityMapper).insert(org.mockito.ArgumentMatchers.any());
        verify(claimVerificationMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(cargoSnapshotMapper, never()).updateBookingStatusByTrackingNumber(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
