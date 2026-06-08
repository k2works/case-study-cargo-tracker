package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.QuotationCommandService;
import com.example.bookingms.application.QuotationQueryService;
import com.example.bookingms.domain.commands.CreateQuotationCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.projections.QuotationSummary;
import com.example.bookingms.interfaces.rest.dto.CreateQuotationRequest;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
import com.example.bookingms.interfaces.rest.dto.QuotationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 輸送見積 REST Controller（US01）。
 *
 * <p>ルート候補（フロントが US07 航海検索結果から選択）を受け取り、最安費用を概算金額とする。
 * 候補が空（期限内ルートなし）でも見積は作成でき、候補なしであることが荷主への通知材料となる。</p>
 */
@RestController
@RequestMapping("/api/v1/quotes")
@PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
public class QuotationController {

    private final QuotationCommandService commandService;
    private final QuotationQueryService queryService;

    public QuotationController(QuotationCommandService commandService, QuotationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateQuotationRequest request) {
        String quotationId = request.quotationId() == null || request.quotationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.quotationId();

        List<RouteCandidate> candidates = request.candidates() == null ? List.of() :
                request.candidates().stream()
                        .map(c -> new RouteCandidate(
                                c.itinerarySummary(),
                                c.estimatedDays(),
                                c.estimatedCost(),
                                c.estimatedCurrency()))
                        .toList();

        BigDecimal estimatedAmount = candidates.stream()
                .map(RouteCandidate::estimatedCost)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        String estimatedCurrency = candidates.isEmpty() ? null : candidates.get(0).estimatedCurrency();

        CreateQuotationCommand command = new CreateQuotationCommand(
                quotationId,
                request.shipperId(),
                new RouteSpecification(
                        request.originUnlocode(),
                        request.destinationUnlocode(),
                        request.arrivalDeadline()),
                new CargoSpecification(
                        CargoType.valueOf(request.cargoType()),
                        request.weightKg(),
                        new Dimensions(0, 0, 0),
                        1,
                        request.productName() == null || request.productName().isBlank()
                                ? "(見積)" : request.productName()),
                candidates,
                estimatedAmount,
                estimatedCurrency,
                request.validUntil());

        commandService.create(command).join();
        return ResponseEntity.status(201).body(Map.of("quotationId", quotationId));
    }

    @GetMapping("/{quotationId}")
    public ResponseEntity<QuotationResponse> findById(@PathVariable String quotationId) {
        QuotationSummary summary = queryService.findById(quotationId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(QuotationResponse.from(summary));
    }

    @GetMapping
    public ResponseEntity<PageResponse<QuotationResponse>> findAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest(page, size);
        List<QuotationResponse> items = queryService.findAll(pageRequest).stream()
                .map(QuotationResponse::from)
                .toList();
        long totalCount = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, totalCount, pageRequest.page(), pageRequest.size()));
    }
}
