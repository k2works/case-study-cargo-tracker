package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.TrackingCommandService;
import com.example.trackingms.application.TrackingQueryService;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.model.JwtToken;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.domain.services.TrackingTokenService;
import com.example.trackingms.interfaces.rest.dto.IssueTokenRequest;
import com.example.trackingms.interfaces.rest.dto.PageResponse;
import com.example.trackingms.interfaces.rest.dto.TokenIssuanceResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingEventResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingSummaryResponse;
import com.example.trackingms.interfaces.rest.dto.UpdateTransportStatusRequest;
import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
 * 追跡情報の REST Controller（US17 / IT5 2.3）。
 *
 * <p>追跡管理者が貨物の現在状態を確認し、必要に応じて状態を手動更新するためのエンドポイント。
 * 公開照会（US18：時限署名トークン）は IT6 で別 Controller として実装する。</p>
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingCommandService commandService;
    private final TrackingQueryService queryService;
    private final TrackingTokenService tokenService;

    public TrackingController(TrackingCommandService commandService,
                              TrackingQueryService queryService,
                              TrackingTokenService tokenService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.tokenService = tokenService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TrackingSummaryResponse>> findAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        List<TrackingSummaryResponse> items = queryService.findAll(safePage * safeSize, safeSize).stream()
                .map(TrackingSummaryResponse::from)
                .toList();
        long total = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, total, safePage, safeSize));
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingSummaryResponse> findByTrackingNumber(
            @PathVariable String trackingNumber) {
        TrackingSummary summary = queryService.findByTrackingNumber(trackingNumber);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(TrackingSummaryResponse.from(summary));
    }

    @GetMapping("/{trackingNumber}/events")
    public ResponseEntity<List<TrackingEventResponse>> findEvents(
            @PathVariable String trackingNumber) {
        if (queryService.findByTrackingNumber(trackingNumber) == null) {
            return ResponseEntity.notFound().build();
        }
        List<TrackingEventResponse> events = queryService.findEvents(trackingNumber).stream()
                .map(TrackingEventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    /**
     * 公開照会用 JWT トークンを発行する（US18 / ADR-0013）。
     *
     * <p>追跡管理者が荷主・荷受人向けに照会 URL を生成するために呼び出す。発行された JWT は
     * {@code /tracking/{tn}?token=<JWT>} のクエリパラメータに埋め込んで荷主に送付される。</p>
     *
     * <p>注記（IT6 review H5）: 認可（ROLE_TRACKER + ROLE_ADMIN）は trackingms 全体に Spring Security
     * 導入時（IT8 想定）に追加する。現状は trackingms に Spring Security が入っていないため、本エンドポイントは
     * 認証なしで実行可能（gateway 側で認可する想定）。</p>
     */
    @PostMapping("/{trackingNumber}/token")
    public ResponseEntity<TokenIssuanceResponse> issueToken(
            @PathVariable String trackingNumber,
            @RequestBody IssueTokenRequest request) {
        TrackingNumber tn;
        try {
            tn = TrackingNumber.of(trackingNumber);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (request == null || request.subjectId() == null || request.subjectId().isBlank()
                || request.role() == null) {
            return ResponseEntity.badRequest().build();
        }
        TrackingSummary summary = queryService.findByTrackingNumber(trackingNumber);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        JwtToken token = tokenService.issue(tn, request.subjectId(), request.role(),
                summary.getDeliveredAt());
        return ResponseEntity.ok(TokenIssuanceResponse.from(token));
    }

    @PostMapping("/{trackingNumber}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String trackingNumber,
                                             @RequestBody UpdateTransportStatusRequest request) {
        TransportStatus toStatus;
        try {
            toStatus = TransportStatus.valueOf(request.toStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                trackingNumber,
                toStatus,
                request.unlocode(),
                request.voyageNumber(),
                request.occurredAt(),
                request.description()
        );
        commandService.updateStatus(command).join();
        return ResponseEntity.accepted().build();
    }

    /**
     * 集約が拒否した状態遷移（不正遷移など）を 422 Unprocessable Entity に変換する。
     * Axon の CommandGateway は集約内例外を {@link CommandExecutionException} でラップし、
     * {@code join()} はさらに {@link CompletionException} でラップするため、
     * 原因の {@link IllegalStateException} を辿って HTTP ステータスに対応付ける。
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", cause.getMessage() == null ? "不正な状態遷移です" : cause.getMessage()));
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", cause.getMessage() == null ? "不正な引数です" : cause.getMessage()));
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
