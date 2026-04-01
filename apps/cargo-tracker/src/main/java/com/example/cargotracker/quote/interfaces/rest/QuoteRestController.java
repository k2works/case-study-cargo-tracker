package com.example.cargotracker.quote.interfaces.rest;

import com.example.cargotracker.quote.application.internal.commandservices.NoRouteAvailableException;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommandService;
import com.example.cargotracker.quote.application.internal.queryservices.FindQuoteQueryService;
import com.example.cargotracker.quote.application.internal.queryservices.QuoteNotFoundException;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.interfaces.rest.dto.QuoteRequest;
import com.example.cargotracker.quote.interfaces.rest.dto.QuoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 見積 REST API コントローラー。
 */
@RestController
@Validated
@RequestMapping("/api/quotes")
public class QuoteRestController {

    private final RegisterQuoteCommandService registerQuoteCommandService;
    private final FindQuoteQueryService findQuoteQueryService;

    public QuoteRestController(RegisterQuoteCommandService registerQuoteCommandService,
                               FindQuoteQueryService findQuoteQueryService) {
        this.registerQuoteCommandService = registerQuoteCommandService;
        this.findQuoteQueryService = findQuoteQueryService;
    }

    @GetMapping
    public List<QuoteResponse> list() {
        return findQuoteQueryService.findAll().stream()
                .map(QuoteResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public QuoteResponse detail(@PathVariable("id") String id) {
        return QuoteResponse.from(findQuote(id));
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> register(@Valid @RequestBody QuoteRequest request,
                                                  UriComponentsBuilder uriComponentsBuilder) {
        Quote quote = registerQuoteCommandService.register(request.toCommand());
        URI location = uriComponentsBuilder.path("/api/quotes/{id}")
                .buildAndExpand(quote.getId().value())
                .toUri();
        return ResponseEntity.created(location).body(QuoteResponse.from(quote));
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleQuoteNotFound(QuoteNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problemDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(NoRouteAvailableException.class)
    public ResponseEntity<ProblemDetail> handleNoRouteAvailable(NoRouteAvailableException e) {
        return ResponseEntity.unprocessableEntity()
                .body(problemDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()));
    }

    private Quote findQuote(String id) {
        try {
            return findQuoteQueryService.findById(new QuoteId(UUID.fromString(id)));
        } catch (IllegalArgumentException _) {
            throw new QuoteNotFoundException(id);
        }
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
