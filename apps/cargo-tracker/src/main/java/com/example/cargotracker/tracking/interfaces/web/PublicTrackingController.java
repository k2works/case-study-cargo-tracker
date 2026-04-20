package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/public/tracking")
public class PublicTrackingController {

    private final TrackingQueryService trackingQueryService;

    public PublicTrackingController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/{trackingNumber}")
    public String showTrackingDetail(
            @PathVariable String trackingNumber,
            Model model) {
        var detail = trackingQueryService.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("tracking", detail);
        return "tracking/detail";
    }
}
