package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.ResolveTrackingExceptionCommand;
import com.example.trackingms.domain.model.TrackingExceptionId;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.projections.TrackingExceptionView;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.interfaces.rest.dto.PageResponse;
import com.example.trackingms.interfaces.rest.dto.RegisterExceptionRequest;
import com.example.trackingms.interfaces.rest.dto.ResolveExceptionRequest;
import com.example.trackingms.interfaces.rest.dto.TrackingExceptionResponse;
import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * 追跡例外管理の REST Controller（US19 / US20 / IT6 タスク 2.3）。
 *
 * <p>POST /tracking/{tn}/exceptions で例外を登録、PATCH .../resolve で解決済みに遷移、
 * GET で一覧・横断照会を提供する。認可は trackingms に Spring Security 導入時（IT8）で
 * ROLE_TRACKER + ROLE_ADMIN（および登録は ROLE_HANDLER）を強制する予定。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingExceptionController {

    private final TrackingCommandService commandService;
    private final TrackingQueryService queryService;

    public TrackingExceptionController(TrackingCommandService commandService,
                                       TrackingQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /**
     * 追跡例外を登録する（US19 / US20）。exceptionId は本 Controller で UUID 採番する。
     */
    @PostMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<TrackingExceptionResponse> register(
            @PathVariable String trackingNumber,
            @RequestBody RegisterExceptionRequest request) {
        try {
            TrackingNumber.of(trackingNumber);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (request == null || request.type() == null || request.occurredAt() == null
                || request.description() == null || request.description().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TrackingSummary summary = queryService.findByTrackingNumber(trackingNumber);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        String exceptionId = TrackingExceptionId.generate().value();
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                trackingNumber,
                exceptionId,
                request.type(),
                request.occurredAt(),
                request.occurredUnlocode(),
                request.description()
        );
        commandService.registerException(command).join();
        // 投影が反映される前でも 201 を返す（クライアントは別途 GET で取得）
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TrackingExceptionResponse(
                        exceptionId,
                        trackingNumber,
                        request.type().name(),
                        request.occurredAt(),
                        request.occurredUnlocode(),
                        request.description(),
                        "REPORTED",
                        null,
                        null,
                        request.type().isEscalationRequired(),
                        null,
                        null
                )
        );
    }

    /**
     * 追跡例外を解決済みに遷移させる（US19 受入基準 4/5、US20 受入基準 5）。
     */
    @PatchMapping("/{trackingNumber}/exceptions/{exceptionId}/resolve")
    public ResponseEntity<Void> resolve(
            @PathVariable String trackingNumber,
            @PathVariable String exceptionId,
            @RequestBody ResolveExceptionRequest request) {
        if (request == null || request.resolution() == null || request.resolution().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        commandService.resolveException(
                new ResolveTrackingExceptionCommand(trackingNumber, exceptionId, request.resolution())
        ).join();
        return ResponseEntity.accepted().build();
    }

    /**
     * 特定追跡番号の例外一覧（US19 / US20）。
     */
    @GetMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<List<TrackingExceptionResponse>> findByTrackingNumber(
            @PathVariable String trackingNumber) {
        if (queryService.findByTrackingNumber(trackingNumber) == null) {
            return ResponseEntity.notFound().build();
        }
        List<TrackingExceptionResponse> items = queryService
                .findExceptionsByTrackingNumber(trackingNumber).stream()
                .map(TrackingExceptionResponse::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    /**
     * 全例外横断一覧（S19 例外対応一覧、US19 / US20）。
     */
    @GetMapping("/exceptions")
    public ResponseEntity<PageResponse<TrackingExceptionResponse>> findAll(
            @RequestParam(value = "responseStatus", required = false) String responseStatus,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        List<TrackingExceptionView> views = queryService.findAllExceptions(
                normalizeResponseStatus(responseStatus),
                safePage * safeSize, safeSize);
        List<TrackingExceptionResponse> items = views.stream()
                .map(TrackingExceptionResponse::from).toList();
        long total = queryService.countExceptions(normalizeResponseStatus(responseStatus));
        return ResponseEntity.ok(PageResponse.of(items, total, safePage, safeSize));
    }

    private static String normalizeResponseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // 不正値は null（全件表示）にフォールバック
        switch (raw) {
            case "REPORTED":
            case "RESPONDING":
            case "RESOLVED":
                return raw;
            default:
                return null;
        }
    }

    /**
     * 集約が拒否した遷移（不正状態など）と引数エラーを HTTP ステータスに対応付ける。
     * {@link TrackingController#handleCompletionException} と同じ規約。
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message",
                            cause.getMessage() == null ? "不正な状態遷移です" : cause.getMessage()));
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message",
                            cause.getMessage() == null ? "不正な引数です" : cause.getMessage()));
        }
        throw ex;
    }

    private Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current != null
                && (current instanceof CompletionException || current instanceof CommandExecutionException)) {
            Throwable next = current.getCause();
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current;
    }
}
