package com.example.cargotracker.trackingms.interfaces.rest;

import com.example.cargotracker.trackingms.application.query.TrackingQueryService;
import com.example.cargotracker.trackingms.domain.model.commands.RegisterTrackingExceptionCommand;
import com.example.cargotracker.trackingms.domain.model.commands.ResolveTrackingExceptionCommand;
import com.example.cargotracker.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.trackingms.interfaces.rest.dto.RegisterTrackingExceptionRequest;
import com.example.cargotracker.trackingms.interfaces.rest.dto.ResolveTrackingExceptionRequest;
import com.example.cargotracker.trackingms.interfaces.rest.dto.TrackingExceptionResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 追跡例外 REST API（US19 / US20）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/tracking/{trackingNumber}/exceptions} — 例外一覧取得</li>
 *   <li>{@code POST /api/v1/tracking/{trackingNumber}/exceptions} — 例外登録（DELAY/DAMAGE/LOSS）</li>
 *   <li>{@code PATCH /api/v1/tracking/{trackingNumber}/exceptions/{exceptionId}/resolve} — 例外解決</li>
 * </ul>
 *
 * <p>LOSS 登録時は {@link ExceptionType#isEscalated()} に基づき WARN ログを出力する。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingExceptionController {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingExceptionController.class);
    private static final String ERROR_CODE = "errorCode";
    private static final String MESSAGE_KEY = "message";
    private static final String TRACKING_NUMBER_KEY = "trackingNumber";
    private static final String SYSTEM_OPERATOR_ID = "system";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final TrackingQueryService queryService;
    private final CommandGateway commandGateway;

    public TrackingExceptionController(
            TrackingQueryService queryService,
            CommandGateway commandGateway) {
        this.queryService = queryService;
        this.commandGateway = commandGateway;
    }

    /**
     * US20 追跡例外一覧取得。
     */
    @GetMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<List<TrackingExceptionResponse>> listExceptions(
            @PathVariable String trackingNumber) {
        if (!queryService.exists(trackingNumber)) {
            return ResponseEntity.notFound().build();
        }
        var records = queryService.findExceptions(trackingNumber);
        return ResponseEntity.ok(records.stream().map(TrackingExceptionResponse::from).toList());
    }

    /**
     * US19 追跡例外登録。LOSS 登録時は管理者向け WARN ログを出力する。
     */
    @PostMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<Map<String, String>> registerException(
            @PathVariable String trackingNumber,
            @RequestBody RegisterTrackingExceptionRequest request) {

        if (!queryService.exists(trackingNumber)) {
            return ResponseEntity.notFound().build();
        }

        ExceptionType exceptionType;
        try {
            exceptionType = ExceptionType.valueOf(request.exceptionType());
        } catch (IllegalArgumentException _) {
            return ResponseEntity.badRequest().body(Map.of(
                    ERROR_CODE, "INVALID_EXCEPTION_TYPE",
                    MESSAGE_KEY, "未知の例外種別: " + request.exceptionType()));
        }

        if (exceptionType.isEscalated()) {
            LOG.warn("[ESCALATION] LOSS 例外が登録されました: trackingNumber={}, operator={}",
                    trackingNumber,
                    (request.operatorId() == null || request.operatorId().isBlank())
                            ? SYSTEM_OPERATOR_ID : request.operatorId());
        }

        var exceptionId = UUID.randomUUID().toString();
        var command = new RegisterTrackingExceptionCommand(
                trackingNumber,
                exceptionId,
                exceptionType.name(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now(),
                request.occurredUnlocode(),
                request.description(),
                (request.operatorId() == null || request.operatorId().isBlank())
                        ? SYSTEM_OPERATOR_ID : request.operatorId());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                TRACKING_NUMBER_KEY, trackingNumber,
                "exceptionId", exceptionId));
    }

    /**
     * US20 追跡例外解決。
     */
    @PatchMapping("/{trackingNumber}/exceptions/{exceptionId}/resolve")
    public ResponseEntity<Map<String, String>> resolveException(
            @PathVariable String trackingNumber,
            @PathVariable String exceptionId,
            @RequestBody ResolveTrackingExceptionRequest request) {

        if (!queryService.exists(trackingNumber)) {
            return ResponseEntity.notFound().build();
        }

        var command = new ResolveTrackingExceptionCommand(
                trackingNumber,
                exceptionId,
                request.resolution(),
                (request.operatorId() == null || request.operatorId().isBlank())
                        ? SYSTEM_OPERATOR_ID : request.operatorId());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.ok(Map.of(
                TRACKING_NUMBER_KEY, trackingNumber,
                "exceptionId", exceptionId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        LOG.debug("Bad request", e);
        return ResponseEntity.badRequest().body(Map.of(
                ERROR_CODE, "VALIDATION_ERROR",
                MESSAGE_KEY, e.getMessage()));
    }

    private void sendAndWaitWithTimeout(Object command) {
        try {
            commandGateway.send(command, Object.class)
                    .orTimeout(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException(
                        "コマンド処理がタイムアウトしました（" + COMMAND_TIMEOUT.toSeconds() + "秒）", cause);
            }
            throw new IllegalStateException("コマンド実行に失敗しました", cause);
        }
    }
}
