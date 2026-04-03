package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.interfaces.web.dto.FreightChargeForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * 輸送料金 Web コントローラー。
 */
@Controller
@RequestMapping("/freight")
public class FreightWebController {

    private static final String VIEW_LIST = "billing/list";
    private static final String VIEW_CALCULATE = "billing/calculate";
    private static final String REDIRECT_FREIGHT = "redirect:/freight";
    private static final String FORM_ATTRIBUTE = "form";
    private static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";

    private final CalculateFreightCommandService calculateFreightCommandService;
    private final FreightChargeQueryService freightChargeQueryService;

    public FreightWebController(CalculateFreightCommandService calculateFreightCommandService,
                                FreightChargeQueryService freightChargeQueryService) {
        this.calculateFreightCommandService = calculateFreightCommandService;
        this.freightChargeQueryService = freightChargeQueryService;
    }

    /**
     * 輸送料金一覧を表示する。
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("freightCharges", freightChargeQueryService.findAll());
        return VIEW_LIST;
    }

    /**
     * 料金算出フォームを表示する。
     */
    @GetMapping("/calculate")
    public String showCalculateForm(Model model) {
        model.addAttribute(FORM_ATTRIBUTE, new FreightChargeForm());
        return VIEW_CALCULATE;
    }

    /**
     * 輸送料金を算出する。
     */
    @PostMapping("/calculate")
    public String calculate(@Valid @ModelAttribute(FORM_ATTRIBUTE) FreightChargeForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return VIEW_CALCULATE;
        }

        try {
            calculateFreightCommandService.calculate(form.toCommand());
        } catch (BookingNotFoundException e) {
            redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
            return REDIRECT_FREIGHT;
        }

        return REDIRECT_FREIGHT;
    }

    /**
     * 輸送料金を確定する。
     */
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable("id") String id) {
        calculateFreightCommandService.confirm(new FreightId(UUID.fromString(id)));
        return REDIRECT_FREIGHT;
    }
}
