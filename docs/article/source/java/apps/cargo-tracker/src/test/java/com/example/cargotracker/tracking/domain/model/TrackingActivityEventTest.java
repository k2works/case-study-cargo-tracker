package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingActivityEventTest {

    @Test
    void 受領イベントを作成できる() {
        LocalDateTime now = LocalDateTime.now();
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.RECEIVE, "JPTYO", now, null);

        assertThat(event.getEventType()).isEqualTo(TrackingEventType.RECEIVE);
        assertThat(event.getLocationUnlocode()).isEqualTo("JPTYO");
        assertThat(event.getCompletionTime()).isEqualTo(now);
        assertThat(event.getVoyageNumber()).isNull();
    }

    @Test
    void 積込イベントを航海番号付きで作成できる() {
        LocalDateTime now = LocalDateTime.now();
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.LOAD, "USLAX", now, "V100");

        assertThat(event.getEventType()).isEqualTo(TrackingEventType.LOAD);
        assertThat(event.getVoyageNumber()).isEqualTo("V100");
    }

    @Test
    void eventTypeがnullの場合は例外() {
        assertThatThrownBy(() -> new TrackingActivityEvent(null, "JPTYO", LocalDateTime.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locationUnlocodeが空の場合は例外() {
        assertThatThrownBy(() -> new TrackingActivityEvent(TrackingEventType.RECEIVE, "", LocalDateTime.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completionTimeがnullの場合は例外() {
        assertThatThrownBy(() -> new TrackingActivityEvent(TrackingEventType.RECEIVE, "JPTYO", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
