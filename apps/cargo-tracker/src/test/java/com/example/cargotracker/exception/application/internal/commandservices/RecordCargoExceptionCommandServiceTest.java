package com.example.cargotracker.exception.application.internal.commandservices;

import com.example.cargotracker.exception.application.internal.outboundservices.TrackingExistencePort;
import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.commands.RecordCargoExceptionCommand;
import com.example.cargotracker.exception.domain.model.events.CargoExceptionRecordedEvent;
import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordCargoExceptionCommandService")
class RecordCargoExceptionCommandServiceTest {

    @Mock
    private CargoExceptionRepository cargoExceptionRepository;

    @Mock
    private TrackingExistencePort trackingExistencePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RecordCargoExceptionCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new RecordCargoExceptionCommandService(
                cargoExceptionRepository, trackingExistencePort, eventPublisher);
    }

    private RecordCargoExceptionCommand validCommand() {
        return new RecordCargoExceptionCommand(
                "TRK-AB123456",
                ExceptionType.DELAY,
                "JPTYO",
                LocalDateTime.of(2026, 5, 28, 10, 0),
                "悪天候",
                "代替船を手配し、到着予定を 2026-06-05 に更新"
        );
    }

    @Test
    @DisplayName("遅延例外を記録すると ExceptionId が返される")
    void execute_delay_returnsExceptionId() {
        doNothing().when(trackingExistencePort).verifyExists("TRK-AB123456");

        ExceptionId result = commandService.execute(validCommand());

        assertThat(result).isNotNull();
        verify(cargoExceptionRepository).save(any(CargoIncident.class));
    }

    @Test
    @DisplayName("存在しない追跡番号の場合は TrackingNotFoundException をスローする")
    void execute_unknownTrackingNumber_throwsTrackingNotFoundException() {
        doThrow(new TrackingNotFoundException("TRK-UNKNOWN"))
                .when(trackingExistencePort).verifyExists("TRK-AB123456");

        RecordCargoExceptionCommand command = validCommand();
        assertThatThrownBy(() -> commandService.execute(command))
                .isInstanceOf(TrackingNotFoundException.class);
        verify(cargoExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("記録後に CargoExceptionRecordedEvent が発行される")
    void execute_publishesCargoExceptionRecordedEvent() {
        doNothing().when(trackingExistencePort).verifyExists("TRK-AB123456");

        commandService.execute(validCommand());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(CargoExceptionRecordedEvent.class::isInstance);
    }

    @Test
    @DisplayName("紛失例外の場合は urgent フラグが true でイベントが発行される")
    void execute_loss_eventUrgentFlagIsTrue() {
        doNothing().when(trackingExistencePort).verifyExists("TRK-AB123456");
        RecordCargoExceptionCommand lossCommand = new RecordCargoExceptionCommand(
                "TRK-AB123456", ExceptionType.LOSS, "SGSIN",
                LocalDateTime.of(2026, 5, 31, 8, 0), "保管中に紛失", "調査を開始"
        );

        commandService.execute(lossCommand);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        CargoExceptionRecordedEvent event = captor.getAllValues().stream()
                .filter(CargoExceptionRecordedEvent.class::isInstance)
                .map(CargoExceptionRecordedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(event.urgent()).isTrue();
    }

    @Test
    @DisplayName("対応内容付きで例外を記録すると保存対象にも引き継がれる")
    void execute_withResolution_persistsResolution() {
        doNothing().when(trackingExistencePort).verifyExists("TRK-AB123456");

        commandService.execute(validCommand());

        ArgumentCaptor<CargoIncident> captor = ArgumentCaptor.forClass(CargoIncident.class);
        verify(cargoExceptionRepository).save(captor.capture());
        assertThat(captor.getValue().getResolution()).isEqualTo("代替船を手配し、到着予定を 2026-06-05 に更新");
    }
}
