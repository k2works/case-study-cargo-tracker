package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.RecordTrackingExceptionCommand;
import com.example.trackingms.application.internal.commandservices.TrackingExceptionService;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingExceptionEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 遅延例外処理 REST コントローラー
 */
@RestController
@RequestMapping("/api/tracking/v1")
public class TrackingExceptionController {

    private final TrackingExceptionService trackingExceptionService;

    public TrackingExceptionController(TrackingExceptionService trackingExceptionService) {
        this.trackingExceptionService = trackingExceptionService;
    }

    /**
     * POST /api/tracking/v1/{trackingNumber}/exceptions — 遅延例外を記録する
     */
    @PostMapping("/{trackingNumber}/exceptions")
    public ResponseEntity<Map<String, Object>> recordException(
            @PathVariable String trackingNumber,
            @RequestBody RecordExceptionRequest request) {

        RecordTrackingExceptionCommand command = new RecordTrackingExceptionCommand(
                trackingNumber,
                request.exceptionType(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now(),
                request.locationUnlocode(),
                request.reason(),
                Boolean.TRUE.equals(request.escalationFlag())
        );

        TrackingActivity activity = trackingExceptionService.recordException(command);

        List<Map<String, Object>> exceptions = activity.getExceptions().stream()
                .map(e -> Map.<String, Object>of(
                        "exceptionType", e.getExceptionType(),
                        "occurredAt", e.getOccurredAt().toString(),
                        "status", e.getStatus()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "trackingNumber", trackingNumber,
                "transportStatus", activity.getTransportStatus().name(),
                "exceptions", exceptions
        ));
    }

    record RecordExceptionRequest(
            String exceptionType,
            LocalDateTime occurredAt,
            String locationUnlocode,
            String reason,
            Boolean escalationFlag
    ) {}
}
