package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.InvoiceCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService.InvoiceSummary;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.commands.ConfirmPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.GenerateInvoiceCommand;
import com.example.cargotracker.billing.interfaces.rest.dto.GenerateInvoiceRequest;
import com.example.cargotracker.billing.interfaces.rest.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 精算書 REST コントローラー。
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceRestController {

    private final InvoiceCommandService invoiceCommandService;
    private final InvoiceQueryService invoiceQueryService;

    public InvoiceRestController(InvoiceCommandService invoiceCommandService,
                                 InvoiceQueryService invoiceQueryService) {
        this.invoiceCommandService = invoiceCommandService;
        this.invoiceQueryService = invoiceQueryService;
    }

    /**
     * 精算書を発行する。201 Created + InvoiceResponse + Location ヘッダーを返す。
     */
    @PostMapping
    public ResponseEntity<InvoiceResponse> generateInvoice(
            @Valid @RequestBody GenerateInvoiceRequest request,
            UriComponentsBuilder uriComponentsBuilder) {

        InvoiceId invoiceId = invoiceCommandService.generateInvoice(
                new GenerateInvoiceCommand(request.bookingId(), request.freightChargeId()));

        InvoiceSummary summary = invoiceQueryService.findById(invoiceId.value().toString())
                .orElseThrow(() -> new IllegalStateException("発行された精算書が見つかりません: " + invoiceId.value()));

        URI location = uriComponentsBuilder.path("/api/v1/invoices/{id}")
                .buildAndExpand(invoiceId.value())
                .toUri();

        return ResponseEntity.created(location).body(toResponse(summary));
    }

    /**
     * 支払いを確認する。200 OK + 更新後の InvoiceResponse を返す。
     */
    @PutMapping("/{id}/confirm-payment")
    public ResponseEntity<InvoiceResponse> confirmPayment(@PathVariable("id") String id) {
        invoiceCommandService.confirmPayment(new ConfirmPaymentCommand(id));

        InvoiceSummary summary = invoiceQueryService.findById(id)
                .orElseThrow(() -> new IllegalStateException("精算書が見つかりません: " + id));

        return ResponseEntity.ok(toResponse(summary));
    }

    /**
     * 精算一覧を返す。200 OK + List<InvoiceResponse>。
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> findAll() {
        List<InvoiceResponse> responses = invoiceQueryService.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 精算書を ID で取得する。200 OK または 404 Not Found。
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> findById(@PathVariable("id") String id) {
        return invoiceQueryService.findById(id)
                .map(summary -> ResponseEntity.ok(toResponse(summary)))
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problemDetail.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    private InvoiceResponse toResponse(InvoiceSummary summary) {
        return new InvoiceResponse(
                summary.id(),
                summary.bookingId(),
                summary.freightChargeId(),
                summary.amount(),
                summary.dueDate(),
                summary.paymentStatus()
        );
    }
}
