package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.ApplyDiscountCommandService;
import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService.FreightChargeSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.ApplyDiscountCommand;
import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import java.util.UUID;
import com.example.cargotracker.billing.interfaces.rest.dto.ApplyDiscountRequest;
import com.example.cargotracker.billing.interfaces.rest.dto.CalculateFreightRequest;
import com.example.cargotracker.billing.interfaces.rest.dto.FreightChargeResponse;
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

/**
 * 輸送料金 REST コントローラー。
 */
@RestController
@RequestMapping("/api/v1/freight-charges")
public class FreightRestController {

    private final CalculateFreightCommandService calculateFreightCommandService;
    private final ApplyDiscountCommandService applyDiscountCommandService;
    private final FreightChargeQueryService freightChargeQueryService;

    public FreightRestController(CalculateFreightCommandService calculateFreightCommandService,
                                 ApplyDiscountCommandService applyDiscountCommandService,
                                 FreightChargeQueryService freightChargeQueryService) {
        this.calculateFreightCommandService = calculateFreightCommandService;
        this.applyDiscountCommandService = applyDiscountCommandService;
        this.freightChargeQueryService = freightChargeQueryService;
    }

    /**
     * 輸送料金を算出する。201 Created + FreightChargeResponse を返す。
     */
    @PostMapping
    public ResponseEntity<FreightChargeResponse> calculate(@Valid @RequestBody CalculateFreightRequest request,
                                                           UriComponentsBuilder uriComponentsBuilder) {
        FreightId freightId = calculateFreightCommandService.calculate(
                new CalculateFreightCommand(request.bookingId(), request.adjustmentAmount()));

        FreightChargeSummary summary = freightChargeQueryService.findById(freightId.value().toString())
                .orElseThrow(() -> new IllegalStateException("算出された輸送料金が見つかりません: " + freightId.value()));

        URI location = uriComponentsBuilder.path("/api/v1/freight-charges/{id}")
                .buildAndExpand(freightId.value())
                .toUri();

        return ResponseEntity.created(location).body(toResponse(summary));
    }

    /**
     * 輸送料金を ID で取得する。
     */
    @GetMapping("/{id}")
    public ResponseEntity<FreightChargeResponse> findById(@PathVariable("id") String id) {
        return freightChargeQueryService.findById(id)
                .map(summary -> ResponseEntity.ok(toResponse(summary)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 輸送料金を確定する。DRAFT → CONFIRMED。200 OK + 更新後の FreightChargeResponse を返す。
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<FreightChargeResponse> confirm(@PathVariable("id") String id) {
        calculateFreightCommandService.confirm(new FreightId(UUID.fromString(id)));

        FreightChargeSummary summary = freightChargeQueryService.findById(id)
                .orElseThrow(() -> new IllegalStateException("輸送料金が見つかりません: " + id));

        return ResponseEntity.ok(toResponse(summary));
    }

    /**
     * 法人割引を適用する。200 OK + 更新後の FreightChargeResponse を返す。
     */
    @PutMapping("/{id}/apply-discount")
    public ResponseEntity<FreightChargeResponse> applyDiscount(
            @PathVariable("id") String id,
            @Valid @RequestBody ApplyDiscountRequest request) {
        applyDiscountCommandService.applyDiscount(new ApplyDiscountCommand(id, request.bookingId()));

        FreightChargeSummary summary = freightChargeQueryService.findById(id)
                .orElseThrow(() -> new IllegalStateException("輸送料金が見つかりません: " + id));

        return ResponseEntity.ok(toResponse(summary));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFound(BookingNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problemDetail.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    private FreightChargeResponse toResponse(FreightChargeSummary summary) {
        return new FreightChargeResponse(
                summary.id(),
                summary.bookingId(),
                summary.status(),
                summary.baseAmount(),
                summary.adjustmentAmount(),
                summary.totalAmount()
        );
    }
}
