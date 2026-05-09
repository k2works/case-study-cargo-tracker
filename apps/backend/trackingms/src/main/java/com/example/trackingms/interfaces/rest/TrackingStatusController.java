package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.TrackingStatusUpdateService;
import com.example.trackingms.application.internal.commandservices.UpdateTrackingStatusCommand;
import com.example.trackingms.application.internal.queryservices.TrackingQueryService;
import com.example.trackingms.domain.exceptions.TrackingActivityNotFoundException;
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
    private final TrackingQueryService trackingQueryService;

    public TrackingStatusController(TrackingStatusUpdateService trackingStatusUpdateService,
                                    TrackingQueryService trackingQueryService) {
        this.trackingStatusUpdateService = trackingStatusUpdateService;
        this.trackingQueryService = trackingQueryService;
    }

    /**
     * 追跡情報を取得する
     * GET /api/tracking/v1/{trackingNumber}
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<Object> getTrackingActivity(@PathVariable String trackingNumber) {
        try {
            TrackingActivity activity = trackingQueryService.findByTrackingNumber(trackingNumber);
            return ResponseEntity.ok(TrackingActivityResponse.from(activity));
        } catch (TrackingActivityNotFoundException e) {
            log.debug("Tracking activity not found: {}", trackingNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.debug("Invalid tracking number format: {}", trackingNumber);
            return ResponseEntity.badRequest()
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
        } catch (TrackingActivityNotFoundException e) {
            log.debug("Tracking activity not found for status update: {}", trackingNumber);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.debug("Failed to update tracking status", e);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
}
