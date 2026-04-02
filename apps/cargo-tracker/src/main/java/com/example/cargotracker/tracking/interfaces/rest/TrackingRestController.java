package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import com.example.cargotracker.tracking.interfaces.rest.dto.TrackingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingRestController {

    private final TrackingQueryService trackingQueryService;

    public TrackingRestController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingResponse> getByTrackingNumber(@PathVariable String trackingNumber) {
        return trackingQueryService.findByTrackingNumber(trackingNumber)
                .map(entry -> ResponseEntity.ok(TrackingResponse.from(entry)))
                .orElse(ResponseEntity.notFound().build());
    }
}
