package com.example.cargotracker.tracking.interfaces.rest;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import com.example.cargotracker.tracking.interfaces.rest.dto.TrackingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingRestController {

    private final TrackingQueryService trackingQueryService;

    public TrackingRestController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<TrackingResponse> getByTrackingNumber(@PathVariable String trackingNumber) {
        return trackingQueryService.findTrackingInfo(trackingNumber)
                .map(dto -> ResponseEntity.ok(TrackingResponse.from(dto)))
                .orElse(ResponseEntity.notFound().build());
    }
}
