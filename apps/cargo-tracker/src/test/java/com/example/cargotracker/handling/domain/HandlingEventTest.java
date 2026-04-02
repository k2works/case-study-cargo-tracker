package com.example.cargotracker.handling.domain;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.events.HandlingEventRecordedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HandlingEvent 集約")
class HandlingEventTest {

    private HandlingEventId anyId() {
        return HandlingEventId.generate();
    }

    private UUID anyBookingId() {
        return UUID.randomUUID();
    }

    private LocalDateTime anyCompletionTime() {
        return LocalDateTime.of(2026, 5, 12, 9, 0);
    }

    @Test
    @DisplayName("荷役イベントを記録できる（LOAD）")
    void recordLoadEvent() {
        HandlingEventId id = anyId();
        UUID bookingId = anyBookingId();
        LocalDateTime completionTime = anyCompletionTime();

        HandlingEvent event = HandlingEvent.recordEvent(id, bookingId, HandlingEventType.LOAD, "JPTYO", completionTime, null);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getBookingId()).isEqualTo(bookingId);
        assertThat(event.getEventType()).isEqualTo(HandlingEventType.LOAD);
        assertThat(event.getLocationCode()).isEqualTo("JPTYO");
        assertThat(event.getCompletionTime()).isEqualTo(completionTime);
        assertThat(event.getMemo()).isNull();
    }

    @Test
    @DisplayName("記録時に HandlingEventRecordedEvent が発行される")
    void recordEmitsEvent() {
        HandlingEvent event = HandlingEvent.recordEvent(anyId(), anyBookingId(), HandlingEventType.LOAD, "JPTYO", anyCompletionTime(), null);

        assertThat(event.getDomainEvents()).hasSize(1);
        assertThat(event.getDomainEvents().get(0)).isInstanceOf(HandlingEventRecordedEvent.class);

        HandlingEventRecordedEvent domainEvent = (HandlingEventRecordedEvent) event.getDomainEvents().get(0);
        assertThat(domainEvent.handlingEventId()).isEqualTo(event.getId());
        assertThat(domainEvent.bookingId()).isEqualTo(event.getBookingId());
        assertThat(domainEvent.eventType()).isEqualTo(HandlingEventType.LOAD);
    }

    @Test
    @DisplayName("再構成時はドメインイベントが発行されない")
    void reconstituteDoesNotEmitEvent() {
        HandlingEvent event = HandlingEvent.reconstitute(
                anyId(), anyBookingId(), HandlingEventType.UNLOAD, "USNYC", anyCompletionTime(), null);

        assertThat(event.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("全荷役イベント種別（LOAD/UNLOAD/CUSTOMS/TRANSHIP）を記録できる")
    void recordAllHandlingEventTypes() {
        for (HandlingEventType type : new HandlingEventType[]{
                HandlingEventType.LOAD, HandlingEventType.UNLOAD,
                HandlingEventType.CUSTOMS, HandlingEventType.TRANSHIP}) {
            HandlingEvent event = HandlingEvent.recordEvent(anyId(), anyBookingId(), type, "JPTYO", anyCompletionTime(), null);
            assertThat(event.getEventType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("メモ付きで手動更新イベントを記録できる")
    void recordManualUpdateWithMemo() {
        String memo = "台風のため保管中";
        HandlingEvent event = HandlingEvent.recordEvent(anyId(), anyBookingId(), HandlingEventType.MANUAL_UPDATE, "JPTYO", anyCompletionTime(), memo);

        assertThat(event.getEventType()).isEqualTo(HandlingEventType.MANUAL_UPDATE);
        assertThat(event.getMemo()).isEqualTo(memo);
    }

    @Test
    @DisplayName("id が null の場合は記録できない")
    void rejectNullId() {
        UUID bookingId = anyBookingId();
        LocalDateTime completionTime = anyCompletionTime();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(null, bookingId, HandlingEventType.LOAD, "JPTYO", completionTime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bookingId が null の場合は記録できない")
    void rejectNullBookingId() {
        HandlingEventId id = anyId();
        LocalDateTime completionTime = anyCompletionTime();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(id, null, HandlingEventType.LOAD, "JPTYO", completionTime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("eventType が null の場合は記録できない")
    void rejectNullEventType() {
        HandlingEventId id = anyId();
        UUID bookingId = anyBookingId();
        LocalDateTime completionTime = anyCompletionTime();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(id, bookingId, null, "JPTYO", completionTime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("locationCode が null の場合は記録できない")
    void rejectNullLocationCode() {
        HandlingEventId id = anyId();
        UUID bookingId = anyBookingId();
        LocalDateTime completionTime = anyCompletionTime();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(id, bookingId, HandlingEventType.LOAD, null, completionTime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("locationCode が空文字の場合は記録できない")
    void rejectBlankLocationCode() {
        HandlingEventId id = anyId();
        UUID bookingId = anyBookingId();
        LocalDateTime completionTime = anyCompletionTime();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(id, bookingId, HandlingEventType.LOAD, "", completionTime, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("completionTime が null の場合は記録できない")
    void rejectNullCompletionTime() {
        HandlingEventId id = anyId();
        UUID bookingId = anyBookingId();
        assertThatThrownBy(() ->
                HandlingEvent.recordEvent(id, bookingId, HandlingEventType.LOAD, "JPTYO", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
