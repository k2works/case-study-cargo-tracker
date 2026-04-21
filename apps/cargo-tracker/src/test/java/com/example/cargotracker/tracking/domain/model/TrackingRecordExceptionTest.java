package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingRecordExceptionTest {

    private final TrackingNumber trackingNumber = TrackingNumber.of("TRK-20260417-ABCD1234");
    private final TrackingBookingId bookingId = TrackingBookingId.of(UUID.randomUUID().toString());

    @Test
    void 遅延例外を記録するとステータスがEXCEPTIONに変わる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        LocalDateTime now = LocalDateTime.now();

        record.addException(ExceptionType.DELAY, "JPTYO", now);

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.EXCEPTION);
        assertThat(record.getHandlingEvents()).hasSize(1);
        assertThat(record.getHandlingEvents().get(0).getEventType()).isEqualTo(TrackingEventType.EXCEPTION);
        assertThat(record.getHandlingEvents().get(0).getLocationUnlocode()).isEqualTo("JPTYO");
    }

    @Test
    void 破損例外を記録するとステータスがEXCEPTIONに変わる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        LocalDateTime now = LocalDateTime.now();

        record.addException(ExceptionType.DAMAGE, "USNYC", now);

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.EXCEPTION);
    }

    @Test
    void 紛失例外を記録するとステータスがEXCEPTIONに変わる() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        LocalDateTime now = LocalDateTime.now();

        record.addException(ExceptionType.LOST, "HKHKG", now);

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.EXCEPTION);
    }

    @Test
    void 例外種別がnullの場合は例外をスロー() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);

        assertThatThrownBy(() ->
                record.addException(null, "JPTYO", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 場所コードがblankの場合は例外をスロー() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);

        assertThatThrownBy(() ->
                record.addException(ExceptionType.DELAY, "", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 発生日時がnullの場合は例外をスロー() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);

        assertThatThrownBy(() ->
                record.addException(ExceptionType.DELAY, "JPTYO", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
