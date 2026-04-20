package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.tracking.application.internal.commandservices.ManualStatusUpdateCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.RecordHandlingEventCommand;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingCommandService;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/tracking")
public class TrackingThymeleafController {

    private final TrackingCommandService trackingCommandService;
    private final TrackingQueryService trackingQueryService;

    public TrackingThymeleafController(
            TrackingCommandService trackingCommandService,
            TrackingQueryService trackingQueryService) {
        this.trackingCommandService = trackingCommandService;
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping
    public String showTrackingSearch(
            @RequestParam(required = false) String trackingNumber) {
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            return "redirect:/tracking/" + trackingNumber.trim();
        }
        return "tracking/search";
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "荷役作業の記録に失敗しました: " + e.getMessage());
        }
        return "redirect:/tracking/handling";
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

    @GetMapping("/status")
    public String showStatusUpdateForm(Model model) {
        model.addAttribute("trackingStatuses", CargoTrackingStatus.values());
        return "tracking/status";
    }

    @PostMapping("/status")
    public String updateTrackingStatus(
            @RequestParam String trackingNumber,
            @RequestParam String newStatus,
            @RequestParam String locationUnlocode,
            @RequestParam String updateTime,
            RedirectAttributes redirectAttributes) {
        try {
            LocalDateTime time = LocalDateTime.parse(updateTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            ManualStatusUpdateCommand command = new ManualStatusUpdateCommand(
                    trackingNumber,
                    CargoTrackingStatus.valueOf(newStatus),
                    locationUnlocode,
                    time
            );
            trackingCommandService.applyManualStatusUpdate(command);
            redirectAttributes.addFlashAttribute("successMessage", "状態を更新しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "状態の更新に失敗しました: " + e.getMessage());
        }
        return "redirect:/tracking/status";
    }
}
