package com.example.billingms.interfaces.rest;

import com.example.billingms.application.internal.commandservices.CalculateInvoiceCommand;
import com.example.billingms.application.internal.commandservices.InvoiceCommandService;
import com.example.billingms.domain.model.aggregates.Invoice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 請求書 REST コントローラー
 */
@RestController
@RequestMapping("/api/billing/v1/invoices")
public class InvoiceController {

    private final InvoiceCommandService invoiceCommandService;

    public InvoiceController(InvoiceCommandService invoiceCommandService) {
        this.invoiceCommandService = invoiceCommandService;
    }

    /**
     * POST /api/billing/v1/invoices/calculate — 料金を算出する
     */
    @PostMapping("/calculate")
    public ResponseEntity<InvoiceResponse> calculate(@RequestBody CalculateRequest request) {
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                request.bookingId(),
                request.shipperId(),
                request.lineItems().stream()
                        .map(i -> new CalculateInvoiceCommand.LineItemInput(i.description(), i.amountValue()))
                        .toList(),
                request.discountRate()
        );

        Invoice invoice = invoiceCommandService.calculate(command);
        return ResponseEntity.ok(toResponse(invoice));
    }

    /**
     * POST /api/billing/v1/invoices/{invoiceId}/confirm — 料金を確定する
     */
    @PostMapping("/{invoiceId}/confirm")
    public ResponseEntity<InvoiceResponse> confirm(@PathVariable Long invoiceId) {
        Invoice invoice = invoiceCommandService.confirm(invoiceId);
        return ResponseEntity.ok(toResponse(invoice));
    }

    /**
     * POST /api/billing/v1/invoices/{invoiceId}/settle — 精算する（US23）
     */
    @PostMapping("/{invoiceId}/settle")
    public ResponseEntity<InvoiceResponse> settle(@PathVariable Long invoiceId) {
        Invoice invoice = invoiceCommandService.settle(invoiceId);
        return ResponseEntity.ok(toResponse(invoice));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<LineItemResponse> items = invoice.getLineItems().stream()
                .map(i -> new LineItemResponse(i.getDescription(), i.getAmount().toLong(), i.getAmount().currency()))
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getBookingId(),
                invoice.getBaseAmount().toLong(),
                invoice.getDiscountRate(),
                invoice.getDiscountAmount().toLong(),
                invoice.getFinalAmount().toLong(),
                invoice.getFinalAmount().currency(),
                invoice.getPaymentStatus().name(),
                invoice.getIssuedAt().toString(),
                invoice.getDueDate().toString(),
                invoice.getPaidAt() != null ? invoice.getPaidAt().toString() : null,
                items
        );
    }

    record CalculateRequest(
            String bookingId,
            String shipperId,
            List<LineItemInput> lineItems,
            BigDecimal discountRate
    ) {}

    record LineItemInput(String description, long amountValue) {}

    record InvoiceResponse(
            Long id,
            String invoiceNumber,
            String bookingId,
            long baseAmountValue,
            BigDecimal discountRate,
            long discountAmountValue,
            long finalAmountValue,
            String currency,
            String paymentStatus,
            String issuedAt,
            String dueDate,
            String paidAt,
            List<LineItemResponse> lineItems
    ) {}

    record LineItemResponse(String description, long amountValue, String currency) {}
}
