package com.example.cargotracker.billing.interfaces.web;

import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.billing.domain.model.commands.ConfirmPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.GenerateInvoiceCommand;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 精算書 Web コントローラー。
 */
@Controller
@RequestMapping("/invoices")
public class InvoiceWebController {

    private static final String VIEW_INVOICES = "billing/invoices";
    private static final String VIEW_INVOICE_DETAIL = "billing/invoice-detail";
    private static final String REDIRECT_INVOICES = "redirect:/invoices";
    private static final String ERROR_MESSAGE_ATTRIBUTE = "errorMessage";
    private static final String SUCCESS_MESSAGE_ATTRIBUTE = "successMessage";

    private final InvoiceCommandService invoiceCommandService;
    private final InvoiceQueryService invoiceQueryService;

    public InvoiceWebController(InvoiceCommandService invoiceCommandService,
                                InvoiceQueryService invoiceQueryService) {
        this.invoiceCommandService = invoiceCommandService;
        this.invoiceQueryService = invoiceQueryService;
    }

    /**
     * 精算一覧を表示する。
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("invoices", invoiceQueryService.findAll());
        return VIEW_INVOICES;
    }

    /**
     * 精算書詳細を表示する。
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return invoiceQueryService.findById(id)
                .map(invoice -> {
                    model.addAttribute("invoice", invoice);
                    return VIEW_INVOICE_DETAIL;
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, "精算書が見つかりません: " + id);
                    return REDIRECT_INVOICES;
                });
    }

    /**
     * 精算書を発行する。
     */
    @PostMapping
    public String generateInvoice(@RequestParam("bookingId") String bookingId,
                                  @RequestParam("freightChargeId") String freightChargeId,
                                  RedirectAttributes redirectAttributes) {
        try {
            invoiceCommandService.generateInvoice(new GenerateInvoiceCommand(bookingId, freightChargeId));
            redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTRIBUTE, "精算書を発行しました");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
        }
        return REDIRECT_INVOICES;
    }

    /**
     * 支払いを確認する。
     */
    @PostMapping("/{id}/confirm-payment")
    public String confirmPayment(@PathVariable("id") String id,
                                 RedirectAttributes redirectAttributes) {
        try {
            invoiceCommandService.confirmPayment(new ConfirmPaymentCommand(id));
            redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE_ATTRIBUTE, "支払いを確認しました");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ERROR_MESSAGE_ATTRIBUTE, e.getMessage());
        }
        return REDIRECT_INVOICES;
    }
}
