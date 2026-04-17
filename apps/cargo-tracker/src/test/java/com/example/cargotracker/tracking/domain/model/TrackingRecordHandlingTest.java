package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingRecordHandlingTest {

    private final TrackingNumber trackingNumber = TrackingNumber.of("TRK-20260417-ABCD1234");
    private final TrackingBookingId bookingId = TrackingBookingId.of(UUID.randomUUID().toString());
    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void 受領イベント追加後にステータスがRECEIVEDになる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.RECEIVE, "JPTYO", now, null));

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.RECEIVED);
    }

    @Test
    void 積込イベント追加後にステータスがLOADEDになる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.RECEIVE, "JPTYO", now, null));
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.LOAD, "JPTYO", now, "V100"));

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.LOADED);
    }

    @Test
    void 荷降しイベント追加後にステータスがUNLOADEDになる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.RECEIVE, "JPTYO", now, null));
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.LOAD, "JPTYO", now, "V100"));
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.UNLOAD, "USLAX", now, "V100"));

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.UNLOADED);
    }

    @Test
    void イベント一覧を取得できる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        record.addHandlingEvent(new TrackingActivityEvent(TrackingEventType.RECEIVE, "JPTYO", now, null));

        assertThat(record.getHandlingEvents()).hasSize(1);
        assertThat(record.getHandlingEvents().get(0).getEventType()).isEqualTo(TrackingEventType.RECEIVE);
    }
}
