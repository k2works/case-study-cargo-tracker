package com.example.cargotracker.exception.interfaces.web;

import com.example.cargotracker.exception.application.internal.commandservices.RecordCargoExceptionCommandService;
import com.example.cargotracker.exception.application.internal.commandservices.TrackingNotFoundException;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.exception.interfaces.web.dto.CargoExceptionForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/exceptions")
public class ExceptionWebController {

    private static final String FORM_ATTRIBUTE = "form";
    private static final String EXCEPTION_TYPES_ATTRIBUTE = "exceptionTypes";
    private static final String SUCCESS_MESSAGE_ATTRIBUTE = "successMessage";
    private static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";
    private static final String VIEW_NEW = "exception/new";
    private static final List<ExceptionType> ALL_EXCEPTION_TYPES = Arrays.asList(ExceptionType.values());

    private final RecordCargoExceptionCommandService recordCargoExceptionCommandService;

    public ExceptionWebController(RecordCargoExceptionCommandService recordCargoExceptionCommandService) {
        this.recordCargoExceptionCommandService = recordCargoExceptionCommandService;
    }

    @GetMapping("/new")
    public String showNewForm(@RequestParam(value = "trackingNumber", required = false) String trackingNumber,
                              Model model) {
        CargoExceptionForm form = new CargoExceptionForm();
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            form.setTrackingNumber(trackingNumber);
        }
        form.setOccurredAt(LocalDateTime.now());
        model.addAttribute(FORM_ATTRIBUTE, form);
        model.addAttribute(EXCEPTION_TYPES_ATTRIBUTE, ALL_EXCEPTION_TYPES);
        return VIEW_NEW;
    }

    @PostMapping("/new")
    public String createException(@Valid @ModelAttribute("form") CargoExceptionForm form,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(EXCEPTION_TYPES_ATTRIBUTE, ALL_EXCEPTION_TYPES);
            return VIEW_NEW;
        }

        try {
            recordCargoExceptionCommandService.execute(form.toCommand());
            String message = ExceptionType.LOSS == form.getExceptionType()
                    ? "例外（%s）を記録しました。緊急フラグが設定されました。管理担当者への通知を手動で行ってください。".formatted(form.getExceptionType().getDisplayName())
                    : "例外（%s）を記録しました。荷主への通知を手動で行ってください。".formatted(form.getExceptionType().getDisplayName());
            redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTRIBUTE, message);
            return "redirect:/exceptions/new";
        } catch (TrackingNotFoundException | IllegalArgumentException e) {
            model.addAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
            model.addAttribute(EXCEPTION_TYPES_ATTRIBUTE, ALL_EXCEPTION_TYPES);
            return VIEW_NEW;
        }
    }
}
