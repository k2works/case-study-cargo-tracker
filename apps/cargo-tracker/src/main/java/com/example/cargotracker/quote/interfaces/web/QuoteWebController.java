package com.example.cargotracker.quote.interfaces.web;

import com.example.cargotracker.quote.application.internal.commandservices.NoRouteAvailableException;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommandService;
import com.example.cargotracker.quote.application.internal.queryservices.FindQuoteQueryService;
import com.example.cargotracker.quote.application.internal.queryservices.QuoteNotFoundException;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.interfaces.web.dto.QuoteRegisterForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * 見積 Web MVC コントローラー。
 */
@Controller
@RequestMapping("/quotes")
public class QuoteWebController {

    private static final String VIEW_REGISTER = "quote/register";

    private final RegisterQuoteCommandService registerQuoteCommandService;
    private final FindQuoteQueryService findQuoteQueryService;

    public QuoteWebController(RegisterQuoteCommandService registerQuoteCommandService,
                              FindQuoteQueryService findQuoteQueryService) {
        this.registerQuoteCommandService = registerQuoteCommandService;
        this.findQuoteQueryService = findQuoteQueryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("quotes", findQuoteQueryService.findAll());
        return "quote/list";
    }

    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new QuoteRegisterForm());
        model.addAttribute("cargoTypes", CargoType.values());
        return VIEW_REGISTER;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") QuoteRegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cargoTypes", CargoType.values());
            return VIEW_REGISTER;
        }

        try {
            Quote quote = registerQuoteCommandService.register(form.toCommand());
            redirectAttributes.addFlashAttribute("createdQuoteNumber", quote.getQuoteNumber().value());
            return "redirect:/quotes/" + quote.getId().value();
        } catch (NoRouteAvailableException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("cargoTypes", CargoType.values());
            return VIEW_REGISTER;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String id, Model model) {
        QuoteId quoteId;
        try {
            quoteId = new QuoteId(UUID.fromString(id));
        } catch (IllegalArgumentException _) {
            throw new QuoteNotFoundException(id);
        }
        Quote quote = findQuoteQueryService.findById(quoteId);
        model.addAttribute("quote", quote);
        return "quote/detail";
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleQuoteNotFound(QuoteNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/404";
    }
}
