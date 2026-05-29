package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.projections.TrackingEvent;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.interfaces.rest.dto.PageResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingEventResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingSummaryResponse;
import com.example.trackingms.interfaces.rest.dto.UpdateTransportStatusRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingControllerTest {

    @Mock
    private TrackingCommandService commandService;

    @Mock
    private TrackingQueryService queryService;

    @InjectMocks
    private TrackingController controller;

    private TrackingSummary sampleSummary() {
        TrackingSummary s = new TrackingSummary();
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setBookingId("B-001");
        s.setCurrentStatus("RECEIVED");
        s.setCurrentUnlocode("JPTYO");
        s.setMisrouted(false);
        return s;
    }

    @Test
    @DisplayName("US17: GET /tracking 一覧で投影をページネーション返却する")
    void 追跡一覧をページネーション返却() {
        when(queryService.findAll(0, 20)).thenReturn(List.of(sampleSummary()));
        when(queryService.count()).thenReturn(1L);

        ResponseEntity<PageResponse<TrackingSummaryResponse>> response =
                controller.findAll(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(1);
        assertThat(response.getBody().page()).isEqualTo(0);
        assertThat(response.getBody().size()).isEqualTo(20);
        assertThat(response.getBody().items().get(0).trackingNumber()).isEqualTo("TRK-AB12CD3456");
    }

    @Test
    @DisplayName("US17: 不正な size（0 や負数）は 20 に補正する")
    void 不正なサイズは補正される() {
        when(queryService.findAll(0, 20)).thenReturn(List.of());
        when(queryService.count()).thenReturn(0L);

        ResponseEntity<PageResponse<TrackingSummaryResponse>> response =
                controller.findAll(-1, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().page()).isEqualTo(0);
        assertThat(response.getBody().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("US17: GET /tracking/{tn} で投影が返る")
    void 追跡サマリを返す() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(sampleSummary());

        ResponseEntity<TrackingSummaryResponse> response =
                controller.findByTrackingNumber("TRK-AB12CD3456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(response.getBody().currentStatus()).isEqualTo("RECEIVED");
        assertThat(response.getBody().misrouted()).isFalse();
    }

    @Test
    @DisplayName("US17: 存在しない追跡番号は 404 を返す")
    void 存在しない追跡番号は404() {
        when(queryService.findByTrackingNumber("TRK-XXXXXXXXXX")).thenReturn(null);

        ResponseEntity<TrackingSummaryResponse> response =
                controller.findByTrackingNumber("TRK-XXXXXXXXXX");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US17: GET /tracking/{tn}/events で履歴を時系列で返す")
    void 履歴を返す() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(sampleSummary());
        TrackingEvent initEvent = new TrackingEvent();
        initEvent.setEventId(1L);
        initEvent.setTrackingNumber("TRK-AB12CD3456");
        initEvent.setEventType("TRACKING_INITIALIZED");
        initEvent.setTransportStatus("NOT_RECEIVED");
        initEvent.setSource("SYSTEM");
        when(queryService.findEvents("TRK-AB12CD3456")).thenReturn(List.of(initEvent));

        ResponseEntity<List<TrackingEventResponse>> response =
                controller.findEvents("TRK-AB12CD3456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).eventType()).isEqualTo("TRACKING_INITIALIZED");
    }

    @Test
    @DisplayName("US17: POST /tracking/{tn}/status で UpdateTransportStatusCommand が送信される")
    void 状態更新コマンドを送信する() {
        when(commandService.updateStatus(any(UpdateTransportStatusCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                "RECEIVED", "JPTYO", null, occurredAt, "東京港で受領");

        ResponseEntity<Void> response = controller.updateStatus("TRK-AB12CD3456", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<UpdateTransportStatusCommand> captor =
                ArgumentCaptor.forClass(UpdateTransportStatusCommand.class);
        verify(commandService).updateStatus(captor.capture());
        UpdateTransportStatusCommand sent = captor.getValue();
        assertThat(sent.trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(sent.toStatus().name()).isEqualTo("RECEIVED");
        assertThat(sent.unlocode()).isEqualTo("JPTYO");
        assertThat(sent.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("US17: 不正な状態名は 400 を返しコマンドは送信しない")
    void 不正な状態名は400() {
        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                "INVALID_STATUS", "JPTYO", null,
                LocalDateTime.of(2026, 7, 20, 10, 0), null);

        ResponseEntity<Void> response = controller.updateStatus("TRK-AB12CD3456", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(commandService, never()).updateStatus(any());
    }

    @Test
    @DisplayName("US17: toStatus が null の場合は 400")
    void toStatusがnullなら400() {
        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                null, "JPTYO", null,
                LocalDateTime.of(2026, 7, 20, 10, 0), null);

        ResponseEntity<Void> response = controller.updateStatus("TRK-AB12CD3456", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(commandService, never()).updateStatus(any());
    }
}
