package com.example.billingms.interfaces.rest;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.ApplyDiscountCommand;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.commands.RecordPaymentCommand;
import com.example.billingms.domain.model.TransportRecord;
import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.interfaces.rest.dto.CalculateInvoiceRequest;
import com.example.billingms.interfaces.rest.dto.InvoiceResponse;
import com.example.billingms.interfaces.rest.dto.RecordPaymentRequest;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 請求書 REST API（US21 / US23 / IT7 タスク 2.5）。
 *
 * <p>S23 請求詳細・算出画面 / S22 請求一覧（Task 4.7）から呼ばれる。</p>
 *
 * <p>認可（IT10 A1.1 / US30）: クラスレベル {@code @PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")}
 * を付与し、URL ルール認可（{@link com.example.billingms.config.HerokuSecurityConfig}）と
 * 二段重層の深層防御を確立する。{@link org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity}
 * は heroku profile のみ有効化、ローカル/テスト profile では認可をバイパス。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
@PreAuthorize("hasAnyRole('ACCOUNTANT', 'ADMIN')")
public class InvoiceController {

    private final CommandGateway commandGateway;
    private final InvoiceQueryService queryService;
    private final Clock clock;

    public InvoiceController(CommandGateway commandGateway,
                             InvoiceQueryService queryService,
                             Clock clock) {
        this.commandGateway = commandGateway;
        this.queryService = queryService;
        this.clock = clock;
    }

    /**
     * 輸送料金算出開始（手動契機、US21）。通常は CargoDeliveredEvent で自動契機。
     */
    @PostMapping
    public ResponseEntity<InvoiceCreationResponse> calculate(@RequestBody CalculateInvoiceRequest request) {
        if (request == null
                || request.bookingId() == null || request.bookingId().isBlank()
                || request.shipperId() == null || request.shipperId().isBlank()
                || request.distanceKm() == null
                || request.weightKg() == null
                || request.cargoType() == null || request.cargoType().isBlank()
                || request.handlingCount() == null
                || request.currency() == null || request.currency().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        TransportRecord transport;
        try {
            transport = new TransportRecord(
                    request.distanceKm(),
                    request.weightKg(),
                    request.cargoType(),
                    request.handlingCount(),
                    request.currency()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        String invoiceId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CalculateInvoiceCommand(
                invoiceId,
                request.bookingId(),
                request.shipperId(),
                transport
        ));

        return ResponseEntity.accepted().body(new InvoiceCreationResponse(invoiceId));
    }

    /**
     * 法人割引適用（US22、IT7 T3.3 / IT8 T4.2）。経理担当者が S23 で「割引を適用」操作。
     *
     * <p>通常時: Invoice 集約が ShipperInfoAcl から契約取得 + CorporateDiscountPolicy で算出。
     * Circuit Breaker OPEN 時: 経理担当者が S23 で手動入力した割引率を {@code manualDiscountRate}
     * として渡し、ACL をバイパスして直接適用。</p>
     */
    @PostMapping("/{invoiceId}/discount")
    public ResponseEntity<Void> applyDiscount(
            @PathVariable String invoiceId,
            @RequestBody(required = false) com.example.billingms.interfaces.rest.dto.ApplyDiscountRequest request) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        java.math.BigDecimal manualRate = request == null ? null : request.manualDiscountRate();
        commandGateway.sendAndWait(new ApplyDiscountCommand(invoiceId, manualRate));
        return ResponseEntity.accepted().build();
    }

    /**
     * 精算書発行（US23、IT7 T4.3、S24 精算書発行画面）。
     * Invoice 集約が InvoiceNumberGenerator で採番 + PaymentDuePolicy で支払期限確定。
     */
    @PostMapping("/{invoiceId}/issue")
    public ResponseEntity<Void> issue(@PathVariable String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        commandGateway.sendAndWait(new IssueInvoiceCommand(invoiceId));
        return ResponseEntity.accepted().build();
    }

    /**
     * 入金記録（US23、IT7 T4.3、S23 詳細画面の「入金確認」操作）。
     * IT7 は完全一致のみ受理。IT8 で部分入金 / 決済機関 webhook 連携を追加する。
     */
    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<PaymentCreationResponse> recordPayment(
            @PathVariable String invoiceId,
            @RequestBody RecordPaymentRequest request) {
        if (invoiceId == null || invoiceId.isBlank()
                || request == null
                || request.paidAmount() == null
                || request.currency() == null || request.currency().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String paymentId = UUID.randomUUID().toString();
        LocalDateTime paidAt = request.paidAt() != null ? request.paidAt() : LocalDateTime.now(clock);
        commandGateway.sendAndWait(new RecordPaymentCommand(
                invoiceId,
                paymentId,
                request.paidAmount(),
                request.currency(),
                paidAt,
                request.paymentMethod(),
                request.externalReference()
        ));
        return ResponseEntity.accepted().body(new PaymentCreationResponse(paymentId));
    }

    /**
     * 督促対象（INVOICED かつ payment_due 超過）一覧（US23、IT7 T4.3、S25 督促一覧画面）。
     * OverdueScheduler 自動発火と独立して経理担当者が即時確認できる。
     */
    @GetMapping("/overdue")
    public ResponseEntity<InvoiceListResponse> findOverdue() {
        List<InvoiceSummary> items = queryService.findOverdueCandidates();
        return ResponseEntity.ok(new InvoiceListResponse(
                items.stream()
                        .map(s -> InvoiceResponse.from(s, queryService.findLinesByInvoiceId(s.getInvoiceId())))
                        .toList(),
                items.size(), 0, items.size()
        ));
    }

    /**
     * 入金履歴取得（US23、IT8 T5.2 / ADR-0019）。
     *
     * <p>S23 詳細画面の支払履歴セクション + cross-service E2E が PaymentDetailRecorded
     * 経由の payment_method / external_reference 反映を確認するために利用する。</p>
     */
    @GetMapping("/{invoiceId}/payments")
    public ResponseEntity<List<com.example.billingms.interfaces.rest.dto.PaymentResponse>> findPayments(
            @PathVariable String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<com.example.billingms.interfaces.rest.dto.PaymentResponse> payments = queryService
                .findPaymentsByInvoiceId(invoiceId).stream()
                .map(com.example.billingms.interfaces.rest.dto.PaymentResponse::from)
                .toList();
        return ResponseEntity.ok(payments);
    }

    /**
     * 請求書詳細取得（US21・US23、S23 表示用）。
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponse> findByInvoiceId(@PathVariable String invoiceId) {
        InvoiceSummary summary = queryService.findByInvoiceId(invoiceId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        List<InvoiceLine> lines = queryService.findLinesByInvoiceId(invoiceId);
        return ResponseEntity.ok(InvoiceResponse.from(summary, lines));
    }

    /**
     * 請求一覧取得（US23、S22 表示用）。
     * {@code status} クエリパラメータで billing_status による絞り込みが可能。
     */
    @GetMapping
    public ResponseEntity<InvoiceListResponse> findAll(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size,
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false) String status) {
        if (size <= 0 || size > 200) size = 20;
        if (page < 0) page = 0;
        int offset = page * size;
        List<InvoiceSummary> items;
        long total;
        if (status != null && !status.isBlank()) {
            items = queryService.findByStatus(status, offset, size);
            total = queryService.countByStatus(status);
        } else {
            items = queryService.findAll(offset, size);
            total = queryService.count();
        }
        return ResponseEntity.ok(new InvoiceListResponse(
                items.stream()
                        .map(s -> InvoiceResponse.from(s, queryService.findLinesByInvoiceId(s.getInvoiceId())))
                        .toList(),
                total, page, size
        ));
    }

    /** 算出結果に対応する識別子のみを返す簡易レスポンス。 */
    public record InvoiceCreationResponse(String invoiceId) {}

    /** 入金記録レスポンス（US23 T4.3、採番された paymentId のみ）。 */
    public record PaymentCreationResponse(String paymentId) {}

    /** ページネーション付き一覧レスポンス（US23）。 */
    public record InvoiceListResponse(List<InvoiceResponse> items, long totalCount, int page, int size) {}
}
