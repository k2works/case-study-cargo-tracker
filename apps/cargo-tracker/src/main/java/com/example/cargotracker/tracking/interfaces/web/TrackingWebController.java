package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 公開追跡ページコントローラー（認証不要）。
 */
@Controller
@RequestMapping("/tracking")
public class TrackingWebController {

    private final TrackingQueryService trackingQueryService;

    public TrackingWebController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/{trackingNumber}")
    public String show(@PathVariable String trackingNumber, Model model) {
        return trackingQueryService.findTrackingInfo(trackingNumber)
                .map(dto -> {
                    model.addAttribute("trackingInfo", dto);
                    return "tracking/show";
                })
                .orElse("error/404");
    }
}
