package com.example.routingms.interfaces.rest;

import com.example.routingms.application.service.RouteFinderService;
import com.example.routingms.interfaces.rest.dto.ItineraryResponse;
import com.example.routingms.interfaces.rest.dto.RoutingRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 経路候補算出 REST コントローラー
 */
@RestController
@RequestMapping("/api/routing/v1/itineraries")
public class RoutingController {

    private final RouteFinderService routeFinderService;

    public RoutingController(RouteFinderService routeFinderService) {
        this.routeFinderService = routeFinderService;
    }

    /**
     * 経路候補を算出する
     */
    @PostMapping
    public ResponseEntity<List<ItineraryResponse>> findItineraries(@RequestBody RoutingRequest request) {
        List<ItineraryResponse> responses = routeFinderService
                .findItineraries(request.originUnlocode(), request.destinationUnlocode(), request.arrivalDeadline())
                .stream()
                .map(ItineraryResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
