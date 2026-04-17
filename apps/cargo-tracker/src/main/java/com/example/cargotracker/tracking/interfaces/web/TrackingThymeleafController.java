package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.commandservices.RecordHandlingEventCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingCommandService;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/tracking")
public class TrackingThymeleafController {

    private final TrackingCommandService trackingCommandService;

    public TrackingThymeleafController(TrackingCommandService trackingCommandService) {
        this.trackingCommandService = trackingCommandService;
    }

    @GetMapping("/handling")
    public String showHandlingForm(Model model) {
        model.addAttribute("eventTypes", TrackingEventType.values());
        return "tracking/handling";
    }

    @PostMapping("/handling")
    public String recordHandlingEvent(
            @RequestParam String trackingNumber,
            @RequestParam String eventType,
            @RequestParam String locationUnlocode,
            @RequestParam String completionTime,
            @RequestParam(required = false) String voyageNumber,
            RedirectAttributes redirectAttributes) {
        try {
            LocalDateTime time = LocalDateTime.parse(completionTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            RecordHandlingEventCommand command = new RecordHandlingEventCommand(
                    trackingNumber,
                    TrackingEventType.valueOf(eventType),
                    locationUnlocode,
                    time,
                    (voyageNumber != null && !voyageNumber.isBlank()) ? voyageNumber : null
            );
            trackingCommandService.recordHandlingEvent(command);
            redirectAttributes.addFlashAttribute("successMessage", "荷役作業を記録しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tracking/handling";
    }
}
