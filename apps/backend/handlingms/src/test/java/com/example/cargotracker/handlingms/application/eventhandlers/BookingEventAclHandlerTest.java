package com.example.cargotracker.handlingms.application.eventhandlers;

import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.handlingms.infrastructure.persistence.CargoSnapshotRecord;
import com.example.cargotracker.shared.events.CargoBookedEvent;
import com.example.cargotracker.shared.events.CargoRoutedEvent;
import com.example.cargotracker.shared.events.TrackingNumberIssuedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("BookingEventAclHandler")
class BookingEventAclHandlerTest {

    private CargoSnapshotMapper mapper;
    private BookingEventAclHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(CargoSnapshotMapper.class);
        handler = new BookingEventAclHandler(mapper);
    }

    @Test
    @DisplayName("CargoBookedEvent を受けて cargo_snapshot に PRELIMINARY で upsert する")
    void cargoBooked_スナップショットを作成() {
        var event = new CargoBookedEvent("B-001", "7", "JPYOK", "USLAX", "GENERAL",
                LocalDate.of(2026, 12, 31), new BigDecimal("100"),
                100, 100, 100, 1, "産業機械", null, null, null, null, null);

        handler.on(event);

        ArgumentCaptor<CargoSnapshotRecord> captor = ArgumentCaptor.forClass(CargoSnapshotRecord.class);
        verify(mapper).upsert(captor.capture());
        CargoSnapshotRecord r = captor.getValue();

        assertThat(r.getBookingId()).isEqualTo("B-001");
        assertThat(r.getOriginUnlocode()).isEqualTo("JPYOK");
        assertThat(r.getDestinationUnlocode()).isEqualTo("USLAX");
        assertThat(r.getCargoType()).isEqualTo("GENERAL");
        assertThat(r.getArrivalDeadline()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(r.getBookingStatus()).isEqualTo("PRELIMINARY");
        assertThat(r.getTrackingNumber()).isNull();
    }

    @Test
    @DisplayName("CargoRoutedEvent を受けて booking_status を ROUTE_PROPOSED に upsert する")
    void cargoRouted_ステータスを更新() {
        var event = new CargoRoutedEvent("B-002");

        handler.on(event);

        verify(mapper).updateBookingStatusByBookingId("B-002", "ROUTE_PROPOSED");
    }

    @Test
    @DisplayName("TrackingNumberIssuedEvent を受けて tracking_number と booking_status を更新する")
    void trackingNumberIssued_追跡番号を更新() {
        var event = new TrackingNumberIssuedEvent("B-003", "TRK-20260810-ABCD1234");

        handler.on(event);

        verify(mapper).updateTrackingNumber("B-003", "TRK-20260810-ABCD1234", "TRACKING_ISSUED");
    }
}
