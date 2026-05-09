package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.TrackingNumberService;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.interfaces.rest.dto.ErrorResponse;
import com.example.trackingms.interfaces.rest.dto.IssueTrackingNumberRequest;
import com.example.trackingms.interfaces.rest.dto.TrackingActivityResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 追跡番号 REST コントローラー
 */
@RestController
@RequestMapping("/api/tracking/v1/numbers")
public class TrackingNumberController {
    private static final Logger log = LoggerFactory.getLogger(TrackingNumberController.class);

    private final TrackingNumberService trackingNumberService;

    public TrackingNumberController(TrackingNumberService trackingNumberService) {
        this.trackingNumberService = trackingNumberService;
    }

    /**
     * 追跡番号を発行する
     * POST /api/tracking/v1/numbers
     */
    @PostMapping
    public ResponseEntity<Object> issueTrackingNumber(
            @Valid @RequestBody IssueTrackingNumberRequest request) {
        try {
            TrackingActivity activity = trackingNumberService.issueTrackingNumber(request.bookingId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(TrackingActivityResponse.from(activity));
        } catch (IllegalArgumentException e) {
            log.debug("Failed to issue tracking number", e);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
}
