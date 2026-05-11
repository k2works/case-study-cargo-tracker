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
    public ResponseEntity<ExceptionResponse> recordException(
            @PathVariable String trackingNumber,
            @RequestBody RecordExceptionRequest request) {

        RecordTrackingExceptionCommand command = new RecordTrackingExceptionCommand(
                trackingNumber,
                request.exceptionType(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now(),
                request.locationUnlocode(),
                request.reason(),
                Boolean.TRUE.equals(request.escalationFlag()),
                request.damageDescription(),
                request.photoUrl(),
                request.lastKnownLocation(),
                request.lastSeenAt()
        );

        TrackingActivity activity = trackingExceptionService.recordException(command);

        return ResponseEntity.ok(toExceptionResponse(trackingNumber, activity));
    }

    /**
     * PUT /api/tracking/v1/{trackingNumber}/exceptions/{id}/response — 対応内容を更新する
     */
    @PutMapping("/{trackingNumber}/exceptions/{id}/response")
    public ResponseEntity<ExceptionResponse> respondToException(
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

    private ExceptionResponse toExceptionResponse(String trackingNumber, TrackingActivity activity) {
        List<ExceptionItemResponse> exceptions = activity.getExceptions().stream()
                .map(e -> new ExceptionItemResponse(
                        e.getId(),
                        e.getExceptionType().name(),
                        e.getOccurredAt().toString(),
                        e.getLocationUnlocode(),
                        e.getReason(),
                        e.getStatus().name(),
                        e.getResponseContent(),
                        e.getNewEstimatedArrival() != null ? e.getNewEstimatedArrival().toString() : null,
                        e.getDamageDescription(),
                        e.getPhotoUrl(),
                        e.getLastKnownLocation(),
                        e.getLastSeenAt() != null ? e.getLastSeenAt().toString() : null
                ))
                .toList();
        return new ExceptionResponse(trackingNumber, activity.getTransportStatus().name(), exceptions);
    }

    record RecordExceptionRequest(
            String exceptionType,
            LocalDateTime occurredAt,
            String locationUnlocode,
            String reason,
            Boolean escalationFlag,
            String damageDescription,
            String photoUrl,
            String lastKnownLocation,
            LocalDateTime lastSeenAt
    ) {}

    record RespondRequest(
            String responseContent,
            LocalDate newEstimatedArrival
    ) {}

    record ExceptionItemResponse(
            Long id,
            String exceptionType,
            String occurredAt,
            String locationUnlocode,
            String reason,
            String status,
            String responseContent,
            String newEstimatedArrival,
            String damageDescription,
            String photoUrl,
            String lastKnownLocation,
            String lastSeenAt
    ) {}

    record ExceptionResponse(
            String trackingNumber,
            String transportStatus,
            List<ExceptionItemResponse> exceptions
    ) {}
}
