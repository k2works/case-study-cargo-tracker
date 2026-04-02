package com.example.cargotracker.handling.interfaces.web;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
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

@Controller
@RequestMapping("/handling")
public class HandlingWebController {

    private static final String EVENT_TYPES_ATTRIBUTE = "eventTypes";
    private static final String VIEW_NEW = "handling/new";
    private static final String REDIRECT_NEW = "redirect:/handling/new";

    private final RecordHandlingEventCommandService recordHandlingEventCommandService;

    public HandlingWebController(RecordHandlingEventCommandService recordHandlingEventCommandService) {
        this.recordHandlingEventCommandService = recordHandlingEventCommandService;
    }

    @GetMapping("/new")
    public String showNewForm(@RequestParam(value = "bookingId", required = false) String bookingId,
                              Model model) {
        HandlingEventForm form = new HandlingEventForm();
        if (bookingId != null && !bookingId.isBlank()) {
            form.setBookingId(bookingId);
        }
        model.addAttribute("form", form);
        model.addAttribute(EVENT_TYPES_ATTRIBUTE, HandlingEventType.values());
        return VIEW_NEW;
    }

    @PostMapping
    public String createHandlingEvent(@Valid @ModelAttribute("form") HandlingEventForm form,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(EVENT_TYPES_ATTRIBUTE, HandlingEventType.values());
            return VIEW_NEW;
        }

        try {
            recordHandlingEventCommandService.execute(form.toCommand());
            redirectAttributes.addFlashAttribute("successMessage", "荷役作業を記録しました。");
            return REDIRECT_NEW;
        } catch (BookingNotFoundException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute(EVENT_TYPES_ATTRIBUTE, HandlingEventType.values());
            return VIEW_NEW;
        }
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public String handleBookingNotFound(BookingNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        model.addAttribute(EVENT_TYPES_ATTRIBUTE, HandlingEventType.values());
        return VIEW_NEW;
    }
}
