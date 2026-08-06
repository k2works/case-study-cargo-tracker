package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommand;
import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/billing")
public class FreightCalculationController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoiceCommandService invoiceCommandService;

    public FreightCalculationController(
            InvoiceQueryService invoiceQueryService,
            InvoiceCommandService invoiceCommandService
    ) {
        this.invoiceQueryService = invoiceQueryService;
        this.invoiceCommandService = invoiceCommandService;
    }

    @GetMapping("/freight-calculation")
    public String form(@RequestParam(required = false) String bookingId, Model model) {
        if (bookingId != null && !bookingId.isBlank()) {
            model.addAttribute("bookingId", bookingId);
            invoiceQueryService.findByBookingId(bookingId)
                    .ifPresent(invoice -> model.addAttribute("invoice", invoice));
        }
        return "billing/freight-calculation";
    }

    @PostMapping("/freight-calculation")
    public String calculate(
            @RequestParam String bookingId,
            @RequestParam(defaultValue = "0") int adjustmentAmount,
            @RequestParam(defaultValue = "") String adjustmentReason,
            RedirectAttributes redirectAttributes
    ) {
        try {
            invoiceCommandService.calculateFreight(
                    new CalculateFreightCommand(bookingId, adjustmentAmount, adjustmentReason)
            );
            redirectAttributes.addFlashAttribute("successMessage", "輸送料金を確定しました。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/billing/freight-calculation?bookingId=" + bookingId;
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/billing/freight-calculation?bookingId=" + bookingId;
        }
        return "redirect:/billing/invoices";
    }
}
