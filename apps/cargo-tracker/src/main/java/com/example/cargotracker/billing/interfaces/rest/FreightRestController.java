package com.example.cargotracker.billing.interfaces.rest;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService;
import com.example.cargotracker.billing.application.internal.queryservices.FreightChargeQueryService.FreightChargeSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
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
    private final FreightChargeQueryService freightChargeQueryService;

    public FreightRestController(CalculateFreightCommandService calculateFreightCommandService,
                                 FreightChargeQueryService freightChargeQueryService) {
        this.calculateFreightCommandService = calculateFreightCommandService;
        this.freightChargeQueryService = freightChargeQueryService;
    }

    /**
     * 輸送料金を算出する。201 Created + FreightChargeResponse を返す。
     */
    @PostMapping
    public ResponseEntity<FreightChargeResponse> calculate(@Valid @RequestBody CalculateFreightRequest request,
                                                           UriComponentsBuilder uriComponentsBuilder) {
        FreightId freightId = calculateFreightCommandService.calculate(
                new CalculateFreightCommand(request.bookingId()));

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
