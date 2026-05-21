package com.example.cargotracker.billingms.interfaces.rest;

import com.example.cargotracker.billingms.application.query.BillingQueryService;
import com.example.cargotracker.billingms.domain.model.commands.CalculateChargeCommand;
import com.example.cargotracker.billingms.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billingms.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billingms.interfaces.rest.dto.CalculateChargeRequest;
import com.example.cargotracker.billingms.interfaces.rest.dto.InvoiceResponse;
import com.example.cargotracker.billingms.interfaces.rest.dto.IssueInvoiceRequest;
import com.example.cargotracker.billingms.interfaces.rest.dto.RecordPaymentRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 精算 REST API（US21-US23）。
 *
 * <ul>
 *   <li>{@code GET /api/v1/billing/invoices} — S22 請求一覧</li>
 *   <li>{@code GET /api/v1/billing/invoices/{invoiceId}} — S23 請求詳細</li>
 *   <li>{@code POST /api/v1/billing/invoices/{invoiceId}/calculate} — 料金算出・確定</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final Logger LOG = LoggerFactory.getLogger(BillingController.class);
    private static final String ERROR_CODE = "errorCode";
    private static final String MESSAGE_KEY = "message";
    private static final String SYSTEM_OPERATOR_ID = "system";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final String INVOICE_ID_KEY = "invoiceId";
    private static final String STATUS_KEY = "status";

    private final BillingQueryService queryService;
    private final CommandGateway commandGateway;

    public BillingController(BillingQueryService queryService, CommandGateway commandGateway) {
        this.queryService = queryService;
        this.commandGateway = commandGateway;
    }

    /**
     * S22 請求一覧（CALCULATED 以上の全 Invoice）。
     */
    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> listInvoices() {
        return ResponseEntity.ok(
                queryService.findAll().stream()
                        .map(InvoiceResponse::from)
                        .toList());
    }

    /**
     * S23 請求詳細。
     */
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable String invoiceId) {
        return queryService.findById(invoiceId)
                .map(invoiceRecord -> ResponseEntity.ok(InvoiceResponse.from(invoiceRecord)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * US21 料金算出・確定。PENDING 状態の Invoice に対して料金を確定する。
     */
    @PostMapping("/invoices/{invoiceId}/calculate")
    public ResponseEntity<Map<String, String>> calculateCharge(
            @PathVariable String invoiceId,
            @RequestBody CalculateChargeRequest request) {

        if (!queryService.exists(invoiceId)) {
            return ResponseEntity.notFound().build();
        }

        var command = new CalculateChargeCommand(
                invoiceId,
                request.baseAmount() != null ? request.baseAmount() : BigDecimal.ZERO,
                request.currencyCode() != null ? request.currencyCode() : "JPY",
                request.discountRate() != null ? request.discountRate() : BigDecimal.ZERO,
                (request.operatorId() == null || request.operatorId().isBlank())
                        ? SYSTEM_OPERATOR_ID : request.operatorId());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.ok(Map.of(
                INVOICE_ID_KEY, invoiceId,
                STATUS_KEY, "CALCULATED"));
    }

    /**
     * US23 督促一覧（INVOICED かつ支払期限超過の Invoice）。
     */
    @GetMapping("/invoices/overdue")
    public ResponseEntity<List<InvoiceResponse>> listOverdueInvoices() {
        return ResponseEntity.ok(
                queryService.findOverdue().stream()
                        .map(InvoiceResponse::from)
                        .toList());
    }

    /**
     * US23 精算書発行（CALCULATED → INVOICED）。
     */
    @PostMapping("/invoices/{invoiceId}/issue")
    public ResponseEntity<Map<String, String>> issueInvoice(
            @PathVariable String invoiceId,
            @RequestBody IssueInvoiceRequest request) {

        if (!queryService.exists(invoiceId)) {
            return ResponseEntity.notFound().build();
        }

        var command = new IssueInvoiceCommand(
                invoiceId,
                request.paymentDue(),
                (request.operatorId() == null || request.operatorId().isBlank())
                        ? SYSTEM_OPERATOR_ID : request.operatorId());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.ok(Map.of(
                INVOICE_ID_KEY, invoiceId,
                STATUS_KEY, "INVOICED"));
    }

    /**
     * US23 入金確認・精算完了（INVOICED → PAID）。
     */
    @PatchMapping("/invoices/{invoiceId}/settle")
    public ResponseEntity<Map<String, String>> recordPayment(
            @PathVariable String invoiceId,
            @RequestBody RecordPaymentRequest request) {

        if (!queryService.exists(invoiceId)) {
            return ResponseEntity.notFound().build();
        }

        var command = new RecordPaymentCommand(
                invoiceId,
                request.paidAmount(),
                request.paymentMethod() != null ? request.paymentMethod() : "BANK_TRANSFER",
                (request.operatorId() == null || request.operatorId().isBlank())
                        ? SYSTEM_OPERATOR_ID : request.operatorId());

        sendAndWaitWithTimeout(command);
        return ResponseEntity.ok(Map.of(
                INVOICE_ID_KEY, invoiceId,
                STATUS_KEY, "PAID"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        LOG.debug("Bad request", e);
        return ResponseEntity.badRequest().body(Map.of(
                ERROR_CODE, "VALIDATION_ERROR",
                MESSAGE_KEY, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        LOG.debug("Conflict", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                ERROR_CODE, "INVALID_STATE",
                MESSAGE_KEY, e.getMessage()));
    }

    private void sendAndWaitWithTimeout(Object command) {
        try {
            commandGateway.send(command, Object.class)
                    .orTimeout(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException(
                        "コマンド処理がタイムアウトしました（" + COMMAND_TIMEOUT.toSeconds() + "秒）", cause);
            }
            throw new IllegalStateException("コマンド実行に失敗しました", cause);
        }
    }
}
