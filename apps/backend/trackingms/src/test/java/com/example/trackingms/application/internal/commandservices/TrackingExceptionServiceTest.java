package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.ExceptionStatus;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingExceptionService テスト")
class TrackingExceptionServiceTest {

    @Mock
    private TrackingActivityRepository trackingActivityRepository;

    private TrackingExceptionService service;

    @BeforeEach
    void setUp() {
        service = new TrackingExceptionService(trackingActivityRepository);
    }

    @Test
    @DisplayName("遅延例外を記録すると貨物状態が EXCEPTION に更新されること")
    void shouldRecordExceptionAndUpdateStatusToException() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.LOADED,
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RecordTrackingExceptionCommand command = new RecordTrackingExceptionCommand(
                "TRK-000001",
                "DELAY",
                LocalDateTime.of(2026, 6, 20, 10, 0),
                "JPTYO",
                "悪天候による遅延",
                false,
                null, null, null, null
        );

        TrackingActivity result = service.recordException(command);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.EXCEPTION);
        assertThat(result.getExceptions()).hasSize(1);
        assertThat(result.getExceptions().get(0).getExceptionType()).isEqualTo(ExceptionType.DELAY);
        assertThat(result.getExceptions().get(0).getReason()).isEqualTo("悪天候による遅延");
        assertThat(result.getExceptions().get(0).getStatus()).isEqualTo(ExceptionStatus.OPEN);
        verify(trackingActivityRepository).update(activity);
    }

    @Test
    @DisplayName("エスカレーションフラグ付きの例外を記録できること")
    void shouldRecordExceptionWithEscalationFlag() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000002"),
                new TrackingBookingId("BK-001235"),
                TrackingStatus.LOADED,
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RecordTrackingExceptionCommand command = new RecordTrackingExceptionCommand(
                "TRK-000002",
                "CUSTOMS_HOLD",
                LocalDateTime.of(2026, 6, 21, 9, 0),
                "JPOSA",
                "通関書類不備",
                true,
                null, null, null, null
        );

        TrackingActivity result = service.recordException(command);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.EXCEPTION);
        assertThat(result.getExceptions().get(0).isEscalationFlag()).isTrue();
    }

    @Test
    @DisplayName("存在しない追跡番号の場合は例外が発生すること")
    void shouldThrowExceptionForNonExistentTrackingNumber() {
        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.empty());

        RecordTrackingExceptionCommand command = new RecordTrackingExceptionCommand(
                "TRK-999999",
                "DELAY",
                LocalDateTime.of(2026, 6, 20, 10, 0),
                "JPTYO",
                "遅延理由",
                false,
                null, null, null, null
        );

        assertThatThrownBy(() -> service.recordException(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-999999");
    }

    @Test
    @DisplayName("例外対応内容を更新できること")
    void shouldRespondToException() {
        TrackingExceptionEvent existingException =
                new TrackingExceptionEvent(
                        10L, ExceptionType.DELAY,
                        java.time.LocalDateTime.of(2026, 6, 20, 10, 0),
                        "JPTYO", "悪天候による遅延", false,
                        null, null, ExceptionStatus.OPEN
                );

        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.EXCEPTION,
                List.of(),
                List.of(existingException)
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RespondToExceptionCommand command = new RespondToExceptionCommand(
                "TRK-000001",
                10L,
                "代替航路を手配しました",
                java.time.LocalDate.of(2026, 6, 25)
        );

        TrackingActivity result = service.respondToException(command);

        assertThat(result.getExceptions()).hasSize(1);
        assertThat(result.getExceptions().get(0).getResponseContent()).isEqualTo("代替航路を手配しました");
        assertThat(result.getExceptions().get(0).getStatus()).isEqualTo(ExceptionStatus.IN_PROGRESS);
        verify(trackingActivityRepository).update(activity);
    }

    @Test
    @DisplayName("存在しない exceptionId の場合は例外が発生すること")
    void shouldThrowExceptionForNonExistentExceptionId() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.EXCEPTION,
                List.of(),
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RespondToExceptionCommand command = new RespondToExceptionCommand(
                "TRK-000001",
                999L,
                "対応内容",
                java.time.LocalDate.of(2026, 6, 25)
        );

        assertThatThrownBy(() -> service.respondToException(command))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
