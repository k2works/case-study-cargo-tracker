package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
import com.example.cargotracker.handling.application.internal.queryservices.FindHandlingEventsQueryService;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import com.example.cargotracker.handling.interfaces.web.dto.HandlingEventForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/handling")
public class HandlingWebController {

    private static final String EVENT_TYPES_ATTRIBUTE = "eventTypes";
    private static final List<HandlingEventType> HANDLING_OPERATION_EVENT_TYPES = List.of(
            HandlingEventType.LOAD,
            HandlingEventType.UNLOAD,
            HandlingEventType.CUSTOMS,
            HandlingEventType.TRANSHIP
    );
    private static final String VIEW_LIST = "handling/list";
    private static final String VIEW_NEW = "handling/new";

    private final RecordHandlingEventCommandService recordHandlingEventCommandService;
    private final FindHandlingEventsQueryService findHandlingEventsQueryService;

    public HandlingWebController(RecordHandlingEventCommandService recordHandlingEventCommandService,
                                 FindHandlingEventsQueryService findHandlingEventsQueryService) {
        this.recordHandlingEventCommandService = recordHandlingEventCommandService;
        this.findHandlingEventsQueryService = findHandlingEventsQueryService;
    }

    @GetMapping
    public String list(@RequestParam(value = "bookingId", required = false) String bookingId,
                       @RequestParam(value = "eventType", required = false) HandlingEventType eventType,
                       @RequestParam(value = "locationCode", required = false) String locationCode,
                       Model model) {
        UUID bookingUuid = parseUuidOrNull(bookingId);
        model.addAttribute("handlingEvents",
                findHandlingEventsQueryService.findFiltered(bookingUuid, eventType, locationCode).stream()
                        .filter(event -> HANDLING_OPERATION_EVENT_TYPES.contains(event.getEventType()))
                        .toList());
        model.addAttribute(EVENT_TYPES_ATTRIBUTE, HANDLING_OPERATION_EVENT_TYPES);
        model.addAttribute("searchBookingId", bookingId != null ? bookingId : "");
        model.addAttribute("searchEventType",
                eventType != null && HANDLING_OPERATION_EVENT_TYPES.contains(eventType) ? eventType : null);
        model.addAttribute("searchLocationCode", locationCode != null ? locationCode : "");
        model.addAttribute("hasSearchFilter",
                (bookingId != null && !bookingId.isBlank()) || eventType != null
                        || (locationCode != null && !locationCode.isBlank()));
        return VIEW_LIST;
    }

    @GetMapping("/new")
    public String showNewForm(@RequestParam(value = "bookingId", required = false) String bookingId,
                              Model model) {
        HandlingEventForm form = new HandlingEventForm();
        if (bookingId != null && !bookingId.isBlank()) {
            form.setBookingId(bookingId);
        }
        model.addAttribute("form", form);
        model.addAttribute(EVENT_TYPES_ATTRIBUTE, HANDLING_OPERATION_EVENT_TYPES);
        return VIEW_NEW;
    }

    @PostMapping
    public String createHandlingEvent(@Valid @ModelAttribute("form") HandlingEventForm form,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        if (form.getEventType() != null && !HANDLING_OPERATION_EVENT_TYPES.contains(form.getEventType())) {
            bindingResult.rejectValue("eventType", "invalid.operationType",
                    "この画面では積み込み・荷降ろし・通関・積み替えのみ記録できます");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute(EVENT_TYPES_ATTRIBUTE, HANDLING_OPERATION_EVENT_TYPES);
            return VIEW_NEW;
        }

        try {
            recordHandlingEventCommandService.execute(form.toCommand());
            redirectAttributes.addFlashAttribute("successMessage",
                    "%s を記録しました。".formatted(form.getEventType().getDisplayName()));
            return "redirect:" + UriComponentsBuilder.fromPath("/handling")
                    .queryParam("bookingId", form.getBookingId())
                    .build()
                    .toUriString();
        } catch (BookingNotFoundException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute(EVENT_TYPES_ATTRIBUTE, HANDLING_OPERATION_EVENT_TYPES);
            return VIEW_NEW;
        }
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public String handleBookingNotFound(BookingNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        model.addAttribute(EVENT_TYPES_ATTRIBUTE, HANDLING_OPERATION_EVENT_TYPES);
        return VIEW_NEW;
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
