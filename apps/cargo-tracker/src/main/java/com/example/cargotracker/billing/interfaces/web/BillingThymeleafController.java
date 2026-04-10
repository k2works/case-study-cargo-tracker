package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.ConfirmPaymentCommand;
import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/billing/invoices")
public class BillingThymeleafController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoiceCommandService invoiceCommandService;

    public BillingThymeleafController(
            InvoiceQueryService invoiceQueryService,
            InvoiceCommandService invoiceCommandService
    ) {
        this.invoiceQueryService = invoiceQueryService;
        this.invoiceCommandService = invoiceCommandService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("invoices", invoiceQueryService.findAll());
        return "billing/invoices/index";
    }

    @GetMapping("/{invoiceId}")
    public String show(@PathVariable String invoiceId, Model model) {
        var invoice = invoiceQueryService.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        model.addAttribute("invoice", invoice);
        return "billing/invoices/show";
    }

    @GetMapping("/{invoiceId}/confirm")
    public String confirmForm(@PathVariable String invoiceId, Model model) {
        var invoice = invoiceQueryService.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        model.addAttribute("invoice", invoice);
        return "billing/invoices/confirm";
    }

    @PostMapping("/{invoiceId}/confirm")
    public String confirm(
            @PathVariable String invoiceId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            invoiceCommandService.confirmPayment(new ConfirmPaymentCommand(invoiceId));
            redirectAttributes.addFlashAttribute("successMessage", "入金を確認しました。");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(NOT_FOUND);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/billing/invoices/" + invoiceId;
    }
}
