package com.example.trackingms.domain.model.aggregates;

import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingEventType;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrackingActivity 集約ルートテスト")
class TrackingActivityTest {

    private TrackingActivity activity;

    @BeforeEach
    void setUp() {
        activity = new TrackingActivity(
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"));
    }

    @Test
    @DisplayName("新規作成時の初期ステータスは NOT_RECEIVED であること")
    void shouldHaveInitialStatusNotReceived() {
        assertThat(activity.getTransportStatus()).isEqualTo(TrackingStatus.NOT_RECEIVED);
        assertThat(activity.getEvents()).isEmpty();
    }

    @Test
    @DisplayName("RECEIVE イベント追加後にステータスが RECEIVED に遷移すること")
    void shouldTransitionToReceivedAfterReceiveEvent() {
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.RECEIVE, "JPTYO",
                LocalDateTime.of(2026, 6, 15, 10, 0), null, null);

        activity.addEvent(event);

        assertThat(activity.getTransportStatus()).isEqualTo(TrackingStatus.RECEIVED);
        assertThat(activity.getEvents()).hasSize(1);
    }

    @Test
    @DisplayName("LOAD イベント追加後にステータスが LOADED に遷移すること")
    void shouldTransitionToLoadedAfterLoadEvent() {
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.LOAD, "JPTYO",
                LocalDateTime.of(2026, 6, 16, 8, 0), "V0042", null);

        activity.addEvent(event);

        assertThat(activity.getTransportStatus()).isEqualTo(TrackingStatus.LOADED);
    }

    @Test
    @DisplayName("UNLOAD イベント追加後にステータスが UNLOADED に遷移すること")
    void shouldTransitionToUnloadedAfterUnloadEvent() {
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.UNLOAD, "CNSHA",
                LocalDateTime.of(2026, 6, 30, 8, 0), "V0042", null);

        activity.addEvent(event);

        assertThat(activity.getTransportStatus()).isEqualTo(TrackingStatus.UNLOADED);
    }

    @Test
    @DisplayName("updateStatus で状態を手動更新できること")
    void shouldUpdateStatusManually() {
        activity.updateStatus(TrackingStatus.EXCEPTION);

        assertThat(activity.getTransportStatus()).isEqualTo(TrackingStatus.EXCEPTION);
    }

    @Test
    @DisplayName("addEvent に null を渡すと例外が発生すること")
    void shouldThrowExceptionWhenAddingNullEvent() {
        assertThatThrownBy(() -> activity.addEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getEvents は変更不可能なリストを返すこと")
    void shouldReturnUnmodifiableEventsList() {
        TrackingActivityEvent newEvent = new TrackingActivityEvent(
                TrackingEventType.RECEIVE, "JPTYO", LocalDateTime.now(), null, null);
        var events = activity.getEvents();
        assertThatThrownBy(() -> events.add(newEvent))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
