package com.example.trackingms.interfaces.rest;

import com.example.trackingms.application.internal.commandservices.RecordHandlingActivityCommand;
import com.example.trackingms.application.internal.commandservices.TrackingActivityEventService;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.interfaces.rest.dto.ErrorResponse;
import com.example.trackingms.interfaces.rest.dto.RecordHandlingActivityRequest;
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
 * 荷役作業記録 REST コントローラー
 */
@RestController
@RequestMapping("/api/handling/v1/activities")
public class HandlingActivityController {
    private static final Logger log = LoggerFactory.getLogger(HandlingActivityController.class);

    private final TrackingActivityEventService trackingActivityEventService;

    public HandlingActivityController(TrackingActivityEventService trackingActivityEventService) {
        this.trackingActivityEventService = trackingActivityEventService;
    }

    /**
     * 荷役作業を記録する
     * POST /api/handling/v1/activities
     */
    @PostMapping
    public ResponseEntity<Object> recordHandlingActivity(
            @Valid @RequestBody RecordHandlingActivityRequest request) {
        try {
            RecordHandlingActivityCommand command = new RecordHandlingActivityCommand(
                    request.trackingNumber(),
                    request.eventType(),
                    request.locationUnlocode(),
                    request.eventTime(),
                    request.voyageNumber()
            );
            TrackingActivity activity = trackingActivityEventService.recordHandlingActivity(command);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(TrackingActivityResponse.from(activity));
        } catch (IllegalArgumentException e) {
            log.debug("Failed to record handling activity", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            log.debug("Invalid event type for handling activity", e);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        }
    }
}
