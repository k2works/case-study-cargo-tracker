package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.TrackingStatusUpdateService;
import com.example.trackingms.application.internal.commandservices.UpdateTrackingStatusCommand;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.interfaces.rest.dto.ErrorResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingActivityResponse;
import com.example.trackingms.interfaces.rest.dto.UpdateTrackingStatusRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 貨物追跡状態 REST コントローラー
 */
@RestController
@RequestMapping("/api/tracking/v1")
public class TrackingStatusController {
    private static final Logger log = LoggerFactory.getLogger(TrackingStatusController.class);

    private final TrackingStatusUpdateService trackingStatusUpdateService;

    public TrackingStatusController(TrackingStatusUpdateService trackingStatusUpdateService) {
        this.trackingStatusUpdateService = trackingStatusUpdateService;
    }

    /**
     * 追跡情報を取得する
     * GET /api/tracking/v1/{trackingNumber}
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<Object> getTrackingActivity(@PathVariable String trackingNumber) {
        try {
            TrackingActivity activity = trackingStatusUpdateService.findByTrackingNumber(trackingNumber);
            return ResponseEntity.ok(TrackingActivityResponse.from(activity));
        } catch (IllegalArgumentException e) {
            log.debug("Tracking activity not found: {}", trackingNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    /**
     * 貨物状態を手動更新する
     * PUT /api/tracking/v1/{trackingNumber}/status
     */
    @PutMapping("/{trackingNumber}/status")
    public ResponseEntity<Object> updateTrackingStatus(
            @PathVariable String trackingNumber,
            @Valid @RequestBody UpdateTrackingStatusRequest request) {
        try {
            UpdateTrackingStatusCommand command = new UpdateTrackingStatusCommand(
                    trackingNumber, request.newStatus());
            TrackingActivity activity = trackingStatusUpdateService.updateStatus(command);
            return ResponseEntity.ok(TrackingActivityResponse.from(activity));
        } catch (IllegalArgumentException e) {
            log.debug("Failed to update tracking status", e);
            String message = e.getMessage();
            if (message != null && message.startsWith("Tracking activity not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(message));
            }
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(message));
        }
    }
}
