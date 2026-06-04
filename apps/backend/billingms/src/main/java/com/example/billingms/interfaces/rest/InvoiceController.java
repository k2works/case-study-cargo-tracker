package com.example.billingms.interfaces.rest;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.ApplyDiscountCommand;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.model.TransportRecord;
import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.interfaces.rest.dto.CalculateInvoiceRequest;
import com.example.billingms.interfaces.rest.dto.InvoiceResponse;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 請求書 REST API（US21 / US23 / IT7 タスク 2.5）。
 *
 * <p>S23 請求詳細・算出画面 / S22 請求一覧（Task 4.7）から呼ばれる。認可は IT8 で
 * Spring Security 統一導入時に {@code @PreAuthorize("hasRole('ACCOUNTANT')")} を付与する
 * （IT7 は trackingms 同様、認証なしで動作）。</p>
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
public class InvoiceController {

    private final CommandGateway commandGateway;
    private final InvoiceQueryService queryService;

    public InvoiceController(CommandGateway commandGateway, InvoiceQueryService queryService) {
        this.commandGateway = commandGateway;
        this.queryService = queryService;
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
     * 法人割引適用（US22、IT7 タスク 3.3）。経理担当者が S23 で「割引を適用」操作。
     * Invoice 集約が ShipperInfoAcl から契約取得 + CorporateDiscountPolicy で算出。
     */
    @PostMapping("/{invoiceId}/discount")
    public ResponseEntity<Void> applyDiscount(@PathVariable String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        commandGateway.sendAndWait(new ApplyDiscountCommand(invoiceId));
        return ResponseEntity.accepted().build();
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
     */
    @GetMapping
    public ResponseEntity<InvoiceListResponse> findAll(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "20") int size) {
        if (size <= 0 || size > 200) size = 20;
        if (page < 0) page = 0;
        int offset = page * size;
        List<InvoiceSummary> items = queryService.findAll(offset, size);
        long total = queryService.count();
        return ResponseEntity.ok(new InvoiceListResponse(
                items.stream()
                        .map(s -> InvoiceResponse.from(s, queryService.findLinesByInvoiceId(s.getInvoiceId())))
                        .toList(),
                total, page, size
        ));
    }

    /** 算出結果に対応する識別子のみを返す簡易レスポンス。 */
    public record InvoiceCreationResponse(String invoiceId) {}

    /** ページネーション付き一覧レスポンス（US23）。 */
    public record InvoiceListResponse(List<InvoiceResponse> items, long totalCount, int page, int size) {}
}
