package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.RecordTrackingExceptionCommand;
import com.example.trackingms.application.internal.commandservices.RespondToExceptionCommand;
import com.example.trackingms.application.internal.commandservices.TrackingExceptionService;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

        return ResponseEntity.ok(toExceptionResponse(trackingNumber, activity));
    }

    /**
     * PUT /api/tracking/v1/{trackingNumber}/exceptions/{id}/response — 対応内容を更新する
     */
    @PutMapping("/{trackingNumber}/exceptions/{id}/response")
    public ResponseEntity<Map<String, Object>> respondToException(
            @PathVariable String trackingNumber,
            @PathVariable Long id,
            @RequestBody RespondRequest request) {

        RespondToExceptionCommand command = new RespondToExceptionCommand(
                trackingNumber,
                id,
                request.responseContent(),
                request.newEstimatedArrival()
        );

        TrackingActivity activity = trackingExceptionService.respondToException(command);

        return ResponseEntity.ok(toExceptionResponse(trackingNumber, activity));
    }

    private Map<String, Object> toExceptionResponse(String trackingNumber, TrackingActivity activity) {
        List<Map<String, Object>> exceptions = activity.getExceptions().stream()
                .map(e -> {
                    var map = new java.util.LinkedHashMap<String, Object>();
                    map.put("id", e.getId());
                    map.put("exceptionType", e.getExceptionType());
                    map.put("occurredAt", e.getOccurredAt().toString());
                    map.put("status", e.getStatus());
                    if (e.getResponseContent() != null) {
                        map.put("responseContent", e.getResponseContent());
                    }
                    if (e.getNewEstimatedArrival() != null) {
                        map.put("newEstimatedArrival", e.getNewEstimatedArrival().toString());
                    }
                    return (Map<String, Object>) map;
                })
                .toList();

        return Map.of(
                "trackingNumber", trackingNumber,
                "transportStatus", activity.getTransportStatus().name(),
                "exceptions", exceptions
        );
    }

    record RecordExceptionRequest(
            String exceptionType,
            LocalDateTime occurredAt,
            String locationUnlocode,
            String reason,
            Boolean escalationFlag
    ) {}

    record RespondRequest(
            String responseContent,
            LocalDate newEstimatedArrival
    ) {}
}
