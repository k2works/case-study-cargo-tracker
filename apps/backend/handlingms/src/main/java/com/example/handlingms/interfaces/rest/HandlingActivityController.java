package com.example.handlingms.interfaces.rest;

import com.example.handlingms.application.HandlingActivityCommandService;
import com.example.handlingms.application.HandlingActivityQueryService;
import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.model.ClaimVerification;
import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.interfaces.rest.dto.HandlingActivityResponse;
import com.example.handlingms.interfaces.rest.dto.PageResponse;
import com.example.handlingms.interfaces.rest.dto.RegisterHandlingActivityRequest;
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
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * 荷役作業 REST Controller（US15・US16 / IT5 3.x）。
 */
@RestController
@RequestMapping("/api/v1/handling")
public class HandlingActivityController {

    private final HandlingActivityCommandService commandService;
    private final HandlingActivityQueryService queryService;

    public HandlingActivityController(HandlingActivityCommandService commandService,
                                      HandlingActivityQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterHandlingActivityRequest req) {
        String activityId = req.activityId() == null || req.activityId().isBlank()
                ? UUID.randomUUID().toString()
                : req.activityId();

        HandlingType handlingType;
        try {
            handlingType = HandlingType.valueOf(req.handlingType());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "不正な荷役種別: " + req.handlingType()));
        }

        ClaimVerification verification = null;
        if (req.claimVerification() != null) {
            try {
                verification = new ClaimVerification(
                        req.claimVerification().consigneeName(),
                        req.claimVerification().signatureRef(),
                        req.claimVerification().confirmationCode(),
                        req.claimVerification().verifiedAt()
                );
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        RegisterHandlingActivityCommand command = new RegisterHandlingActivityCommand(
                activityId,
                req.trackingNumber(),
                handlingType,
                req.occurredAt(),
                req.unlocode(),
                req.voyageNumber(),
                req.handlerId(),
                verification
        );

        commandService.register(command).join();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("activityId", activityId));
    }

    @GetMapping
    public ResponseEntity<PageResponse<HandlingActivityResponse>> findAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "trackingNumber", required = false) String trackingNumber) {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            List<HandlingActivityResponse> items = queryService.findByTrackingNumber(trackingNumber).stream()
                    .map(HandlingActivityResponse::from)
                    .toList();
            return ResponseEntity.ok(PageResponse.of(items, items.size(), 0, items.size()));
        }
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        List<HandlingActivityResponse> items = queryService.findAll(safePage * safeSize, safeSize).stream()
                .map(HandlingActivityResponse::from)
                .toList();
        long total = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, total, safePage, safeSize));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<HandlingActivityResponse> findById(@PathVariable String activityId) {
        var summary = queryService.findById(activityId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(HandlingActivityResponse.from(summary));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("message", cause.getMessage() == null ? "不正な操作" : cause.getMessage()));
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", cause.getMessage() == null ? "不正な引数" : cause.getMessage()));
        }
        throw ex;
    }

    private Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current != null
                && (current instanceof CompletionException || current instanceof CommandExecutionException)) {
            Throwable next = current.getCause();
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }
}
