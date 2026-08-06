package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/public/tracking")
public class PublicTrackingController {

    private final TrackingQueryService trackingQueryService;

    public PublicTrackingController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping
    public String showPublicSearch(
            @RequestParam(required = false) String trackingNumber) {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            return "redirect:/public/tracking/" + trackingNumber.trim();
        }
        return "tracking/public-search";
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
