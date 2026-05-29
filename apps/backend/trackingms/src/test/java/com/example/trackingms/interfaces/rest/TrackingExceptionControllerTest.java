package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.ResolveTrackingExceptionCommand;
import com.example.trackingms.domain.model.ExceptionType;
import com.example.trackingms.domain.projections.TrackingExceptionView;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.interfaces.rest.dto.PageResponse;
import com.example.trackingms.interfaces.rest.dto.RegisterExceptionRequest;
import com.example.trackingms.interfaces.rest.dto.ResolveExceptionRequest;
import com.example.trackingms.interfaces.rest.dto.TrackingExceptionResponse;
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
class TrackingExceptionControllerTest {

    @Mock
    private TrackingCommandService commandService;

    @Mock
    private TrackingQueryService queryService;

    @InjectMocks
    private TrackingExceptionController controller;

    private TrackingSummary sampleSummary() {
        TrackingSummary s = new TrackingSummary();
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setBookingId("B-001");
        s.setCurrentStatus("RECEIVED");
        return s;
    }

    private TrackingExceptionView sampleView() {
        TrackingExceptionView v = new TrackingExceptionView();
        v.setExceptionId("EX-001");
        v.setTrackingNumber("TRK-AB12CD3456");
        v.setExceptionType("DELAY");
        v.setOccurredAt(LocalDateTime.of(2026, 7, 25, 9, 0));
        v.setOccurredUnlocode("SGSIN");
        v.setDescription("悪天候");
        v.setResponseStatus("REPORTED");
        v.setEscalated(false);
        return v;
    }

    @Test
    @DisplayName("US19: POST /tracking/{tn}/exceptions で 201 を返し RegisterTrackingExceptionCommand を送信する")
    void 例外を登録できる() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(sampleSummary());
        when(commandService.registerException(any(RegisterTrackingExceptionCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        RegisterExceptionRequest request = new RegisterExceptionRequest(
                ExceptionType.DELAY,
                LocalDateTime.of(2026, 7, 25, 9, 0),
                "SGSIN", "悪天候のため寄港不可");

        ResponseEntity<TrackingExceptionResponse> response =
                controller.register("TRK-AB12CD3456", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type()).isEqualTo("DELAY");
        assertThat(response.getBody().responseStatus()).isEqualTo("REPORTED");
        assertThat(response.getBody().escalated()).isFalse();

        ArgumentCaptor<RegisterTrackingExceptionCommand> cap =
                ArgumentCaptor.forClass(RegisterTrackingExceptionCommand.class);
        verify(commandService).registerException(cap.capture());
        assertThat(cap.getValue().trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(cap.getValue().type()).isEqualTo(ExceptionType.DELAY);
        assertThat(cap.getValue().exceptionId()).isNotBlank();
    }

    @Test
    @DisplayName("US20: LOSS は escalated = true でレスポンスされる")
    void LOSS例外はescalated() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(sampleSummary());
        when(commandService.registerException(any(RegisterTrackingExceptionCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<TrackingExceptionResponse> response = controller.register(
                "TRK-AB12CD3456",
                new RegisterExceptionRequest(ExceptionType.LOSS,
                        LocalDateTime.of(2026, 7, 26, 14, 0), "CNSHA", "貨物紛失"));

        assertThat(response.getBody().escalated()).isTrue();
    }

    @Test
    @DisplayName("US19: 不正な追跡番号フォーマットは 400")
    void 不正な追跡番号は400() {
        ResponseEntity<TrackingExceptionResponse> response = controller.register(
                "bad-format",
                new RegisterExceptionRequest(ExceptionType.DELAY, LocalDateTime.now(), null, "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(commandService, never()).registerException(any());
    }

    @Test
    @DisplayName("US19: 存在しない追跡番号は 404")
    void 存在しない追跡番号は404() {
        when(queryService.findByTrackingNumber("TRK-NOPE000000")).thenReturn(null);

        ResponseEntity<TrackingExceptionResponse> response = controller.register(
                "TRK-NOPE000000",
                new RegisterExceptionRequest(ExceptionType.DELAY,
                        LocalDateTime.now(), "JPTYO", "test"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(commandService, never()).registerException(any());
    }

    @Test
    @DisplayName("US19: description が空ならば 400")
    void descriptionが空は400() {
        ResponseEntity<TrackingExceptionResponse> response = controller.register(
                "TRK-AB12CD3456",
                new RegisterExceptionRequest(ExceptionType.DELAY,
                        LocalDateTime.now(), "JPTYO", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US19: PATCH /resolve で 202 を返し ResolveTrackingExceptionCommand を送信する")
    void 例外を解決できる() {
        when(commandService.resolveException(any(ResolveTrackingExceptionCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ResponseEntity<Void> response = controller.resolve(
                "TRK-AB12CD3456", "EX-001",
                new ResolveExceptionRequest("代替ルート手配済み"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<ResolveTrackingExceptionCommand> cap =
                ArgumentCaptor.forClass(ResolveTrackingExceptionCommand.class);
        verify(commandService).resolveException(cap.capture());
        assertThat(cap.getValue().exceptionId()).isEqualTo("EX-001");
        assertThat(cap.getValue().resolution()).isEqualTo("代替ルート手配済み");
    }

    @Test
    @DisplayName("US19: resolution が空ならば 400")
    void resolutionが空は400() {
        ResponseEntity<Void> response = controller.resolve(
                "TRK-AB12CD3456", "EX-001",
                new ResolveExceptionRequest(""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(commandService, never()).resolveException(any());
    }

    @Test
    @DisplayName("US19: GET /tracking/{tn}/exceptions で投影一覧を返す")
    void 追跡番号別の例外一覧() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(sampleSummary());
        when(queryService.findExceptionsByTrackingNumber("TRK-AB12CD3456"))
                .thenReturn(List.of(sampleView()));

        ResponseEntity<List<TrackingExceptionResponse>> response =
                controller.findByTrackingNumber("TRK-AB12CD3456");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).exceptionId()).isEqualTo("EX-001");
    }

    @Test
    @DisplayName("US19: 存在しない追跡番号の一覧は 404")
    void 一覧_存在しない追跡番号は404() {
        when(queryService.findByTrackingNumber("TRK-NOPE000000")).thenReturn(null);

        ResponseEntity<List<TrackingExceptionResponse>> response =
                controller.findByTrackingNumber("TRK-NOPE000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US19/US20: GET /tracking/exceptions で responseStatus フィルタつき一覧を返す")
    void 全例外横断一覧() {
        when(queryService.findAllExceptions("REPORTED", 0, 20))
                .thenReturn(List.of(sampleView()));
        when(queryService.countExceptions("REPORTED")).thenReturn(1L);

        ResponseEntity<PageResponse<TrackingExceptionResponse>> response =
                controller.findAll("REPORTED", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("US19: 不正な responseStatus は null（全件）にフォールバック")
    void 不正なステータスは全件() {
        when(queryService.findAllExceptions(null, 0, 20))
                .thenReturn(List.of(sampleView()));
        when(queryService.countExceptions(null)).thenReturn(5L);

        ResponseEntity<PageResponse<TrackingExceptionResponse>> response =
                controller.findAll("INVALID", 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalCount()).isEqualTo(5);
    }
}
