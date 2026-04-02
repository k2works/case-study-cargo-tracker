package com.example.cargotracker.handling.application.internal.commandservices;

import com.example.cargotracker.handling.application.internal.outboundservices.BookingExistencePort;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.commands.RecordHandlingEventCommand;
import com.example.cargotracker.handling.domain.model.events.HandlingEventRecordedEvent;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordHandlingEventCommandService")
class RecordHandlingEventCommandServiceTest {

    @Mock
    private HandlingEventRepository handlingEventRepository;

    @Mock
    private BookingExistencePort bookingExistencePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RecordHandlingEventCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new RecordHandlingEventCommandService(handlingEventRepository, bookingExistencePort, eventPublisher);
    }

    private RecordHandlingEventCommand validCommand(UUID bookingId) {
        return new RecordHandlingEventCommand(
                bookingId,
                HandlingEventType.LOAD,
                "JPTYO",
                LocalDateTime.of(2026, 5, 12, 9, 0),
                null
        );
    }

    @Test
    @DisplayName("荷役イベントを記録すると HandlingEventId が返される")
    void recordEventReturnsId() {
        UUID bookingId = UUID.randomUUID();
        doNothing().when(bookingExistencePort).verifyExists(bookingId);

        HandlingEventId result = commandService.execute(validCommand(bookingId));

        assertThat(result).isNotNull();
        verify(handlingEventRepository).save(any());
    }

    @Test
    @DisplayName("存在しない予約 ID の場合は BookingNotFoundException を投げる")
    void throwWhenBookingNotFound() {
        UUID unknownId = UUID.randomUUID();
        doThrow(new BookingNotFoundException(unknownId.toString()))
                .when(bookingExistencePort).verifyExists(unknownId);
        RecordHandlingEventCommand command = validCommand(unknownId);

        assertThatThrownBy(() -> commandService.execute(command))
                .isInstanceOf(BookingNotFoundException.class);
        verify(handlingEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("記録後に HandlingEventRecordedEvent が発行される")
    void publishEventAfterRecord() {
        UUID bookingId = UUID.randomUUID();
        doNothing().when(bookingExistencePort).verifyExists(bookingId);

        commandService.execute(validCommand(bookingId));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(HandlingEventRecordedEvent.class::isInstance);
    }

    @Test
    @DisplayName("メモ付き MANUAL_UPDATE イベントを記録できる")
    void recordManualUpdateWithMemo() {
        UUID bookingId = UUID.randomUUID();
        doNothing().when(bookingExistencePort).verifyExists(bookingId);

        RecordHandlingEventCommand command = new RecordHandlingEventCommand(
                bookingId, HandlingEventType.MANUAL_UPDATE, "JPTYO",
                LocalDateTime.of(2026, 5, 12, 9, 0), "台風のため保管中");

        HandlingEventId result = commandService.execute(command);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("RECEIVE イベントを初回は記録できる")
    void receiveEvent_firstTime_succeeds() {
        UUID bookingId = UUID.randomUUID();
        doNothing().when(bookingExistencePort).verifyExists(bookingId);
        when(handlingEventRepository.findByBookingId(bookingId)).thenReturn(List.of());

        RecordHandlingEventCommand command = new RecordHandlingEventCommand(
                bookingId, HandlingEventType.RECEIVE, "JPTYO",
                LocalDateTime.of(2026, 5, 12, 9, 0), null);

        HandlingEventId result = commandService.execute(command);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("RECEIVE イベントが既に存在する場合は DuplicateReceiveException をスローする")
    void receiveEvent_alreadyExists_throwsDuplicateReceiveException() {
        UUID bookingId = UUID.randomUUID();
        doNothing().when(bookingExistencePort).verifyExists(bookingId);
        HandlingEvent existing = HandlingEvent.reconstitute(
                HandlingEventId.generate(), bookingId, HandlingEventType.RECEIVE,
                "JPTYO", LocalDateTime.of(2026, 5, 1, 9, 0), null);
        when(handlingEventRepository.findByBookingId(bookingId)).thenReturn(List.of(existing));

        RecordHandlingEventCommand command = new RecordHandlingEventCommand(
                bookingId, HandlingEventType.RECEIVE, "JPTYO",
                LocalDateTime.of(2026, 5, 12, 9, 0), null);

        assertThatThrownBy(() -> commandService.execute(command))
                .isInstanceOf(DuplicateReceiveException.class)
                .hasMessageContaining(bookingId.toString());
        verify(handlingEventRepository, never()).save(any());
    }
}
