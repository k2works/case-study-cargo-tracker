package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
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
@DisplayName("TrackingActivityEventService テスト")
class TrackingActivityEventServiceTest {

    @Mock
    private TrackingActivityRepository trackingActivityRepository;

    private TrackingActivityEventService service;

    @BeforeEach
    void setUp() {
        service = new TrackingActivityEventService(trackingActivityRepository);
    }

    @Test
    @DisplayName("RECEIVE イベントを記録すると貨物状態が RECEIVED に更新されること")
    void shouldRecordReceiveEventAndUpdateStatusToReceived() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.NOT_RECEIVED,
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RecordHandlingActivityCommand command = new RecordHandlingActivityCommand(
                "TRK-000001", "RECEIVE", "JPTYO",
                LocalDateTime.of(2026, 6, 15, 10, 0), null, null
        );

        TrackingActivity result = service.recordHandlingActivity(command);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.RECEIVED);
        assertThat(result.getEvents()).hasSize(1);
        verify(trackingActivityRepository).update(activity);
    }

    @Test
    @DisplayName("LOAD イベントを記録すると貨物状態が LOADED に更新されること")
    void shouldRecordLoadEventAndUpdateStatusToLoaded() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.RECEIVED,
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RecordHandlingActivityCommand command = new RecordHandlingActivityCommand(
                "TRK-000001", "LOAD", "JPTYO",
                LocalDateTime.of(2026, 6, 16, 8, 0), "V0042", null
        );

        TrackingActivity result = service.recordHandlingActivity(command);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.LOADED);
        verify(trackingActivityRepository).update(activity);
    }

    @Test
    @DisplayName("CLAIM イベントを記録すると貨物状態が CLAIMED に更新され荷受人確認情報が保存されること")
    void shouldRecordClaimEventWithConsigneeConfirmation() {
        TrackingActivity activity = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId("BK-001234"),
                TrackingStatus.UNLOADED,
                List.of()
        );

        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.of(activity));

        RecordHandlingActivityCommand command = new RecordHandlingActivityCommand(
                "TRK-000001", "CLAIM", "JPTYO",
                LocalDateTime.of(2026, 7, 10, 14, 0), null, "山田太郎"
        );

        TrackingActivity result = service.recordHandlingActivity(command);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.CLAIMED);
        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getEvents().get(0).getConsigneeConfirmation()).isEqualTo("山田太郎");
        verify(trackingActivityRepository).update(activity);
    }

    @Test
    @DisplayName("存在しない追跡番号の場合は例外が発生すること")
    void shouldThrowExceptionForNonExistentTrackingNumber() {
        when(trackingActivityRepository.findByTrackingNumber(any())).thenReturn(Optional.empty());

        RecordHandlingActivityCommand command = new RecordHandlingActivityCommand(
                "TRK-999999", "RECEIVE", "JPTYO",
                LocalDateTime.of(2026, 6, 15, 10, 0), null, null
        );

        assertThatThrownBy(() -> service.recordHandlingActivity(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-999999");
    }
}
