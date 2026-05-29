package com.example.handlingms.interfaces.rest;

import com.example.handlingms.application.HandlingActivityCommandService;
import com.example.handlingms.application.HandlingActivityQueryService;
import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.HandlingActivitySummary;
import com.example.handlingms.interfaces.rest.dto.HandlingActivityResponse;
import com.example.handlingms.interfaces.rest.dto.PageResponse;
import com.example.handlingms.interfaces.rest.dto.RegisterHandlingActivityRequest;
import org.axonframework.commandhandling.CommandExecutionException;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlingActivityControllerTest {

    @Mock
    private HandlingActivityCommandService commandService;

    @Mock
    private HandlingActivityQueryService queryService;

    @InjectMocks
    private HandlingActivityController controller;

    private HandlingActivitySummary summary() {
        HandlingActivitySummary s = new HandlingActivitySummary();
        s.setActivityId("A-001");
        s.setBookingId("B-001");
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setHandlingType("RECEIVE");
        s.setUnlocode("JPTYO");
        s.setHandlerId("H-001");
        return s;
    }

    private RegisterHandlingActivityRequest req(String type) {
        return new RegisterHandlingActivityRequest(
                null, "TRK-AB12CD3456", type,
                LocalDateTime.of(2026, 7, 20, 10, 0),
                "JPTYO", null, "H-001", null);
    }

    @Test
    @DisplayName("US15: 登録で 201 + 自動採番 activityId が返る")
    void 登録で201と採番() {
        when(commandService.register(any(RegisterHandlingActivityCommand.class)))
                .thenReturn(CompletableFuture.completedFuture("ok"));

        ResponseEntity<Map<String, String>> response = controller.register(req("RECEIVE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("activityId")).isNotBlank();

        ArgumentCaptor<RegisterHandlingActivityCommand> captor =
                ArgumentCaptor.forClass(RegisterHandlingActivityCommand.class);
        verify(commandService).register(captor.capture());
        assertThat(captor.getValue().handlingType()).isEqualTo(HandlingType.RECEIVE);
        assertThat(captor.getValue().trackingNumber()).isEqualTo("TRK-AB12CD3456");
    }

    @Test
    @DisplayName("US15: 不正な handlingType は 400")
    void 不正なhandlingTypeは400() {
        ResponseEntity<Map<String, String>> response = controller.register(req("INVALID"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(commandService, never()).register(any());
    }

    @Test
    @DisplayName("US16: CLAIM で荷受人確認なしは 400 を返す（ClaimVerification 生成時の IllegalArgumentException）")
    void CLAIMで確認手段なしは400() {
        RegisterHandlingActivityRequest request = new RegisterHandlingActivityRequest(
                null, "TRK-AB12CD3456", "CLAIM",
                LocalDateTime.of(2026, 8, 16, 14, 0),
                "USNYC", null, "H-001",
                new RegisterHandlingActivityRequest.ClaimVerificationRequest(
                        "山田太郎", null, null,
                        LocalDateTime.of(2026, 8, 16, 14, 0)));

        ResponseEntity<Map<String, String>> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).contains("署名");
    }

    @Test
    @DisplayName("US15: GET /handling 一覧（ページネーション）")
    void 一覧でページネーション返却() {
        when(queryService.findAll(0, 20)).thenReturn(List.of(summary()));
        when(queryService.count()).thenReturn(1L);

        ResponseEntity<PageResponse<HandlingActivityResponse>> response =
                controller.findAll(0, 20, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("US15: trackingNumber 指定時は findByTrackingNumber で取得")
    void trackingNumber指定でフィルタ() {
        when(queryService.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(List.of(summary()));

        ResponseEntity<PageResponse<HandlingActivityResponse>> response =
                controller.findAll(0, 20, "TRK-AB12CD3456");

        assertThat(response.getBody().items()).hasSize(1);
        verify(queryService).findByTrackingNumber("TRK-AB12CD3456");
    }

    @Test
    @DisplayName("US15: 単体取得で 200 / 存在しないなら 404")
    void 単体取得() {
        when(queryService.findById("A-001")).thenReturn(summary());
        when(queryService.findById("A-NONE")).thenReturn(null);

        assertThat(controller.findById("A-001").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.findById("A-NONE").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ExceptionHandler: IllegalStateException → 422")
    void IllegalStateExceptionは422() {
        CompletionException ex = new CompletionException(
                new CommandExecutionException("rejected", new IllegalStateException("不正な操作")));

        ResponseEntity<Map<String, String>> response = controller.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("message")).contains("不正な操作");
    }

    @Test
    @DisplayName("ExceptionHandler: IllegalArgumentException → 400")
    void IllegalArgumentExceptionは400() {
        CompletionException ex = new CompletionException(
                new CommandExecutionException("bad", new IllegalArgumentException("不正な引数")));

        ResponseEntity<Map<String, String>> response = controller.handleCompletionException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
