package com.example.cargotracker.quote.interfaces.rest;

import com.example.cargotracker.quote.application.internal.commandservices.NoRouteAvailableException;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommandService;
import com.example.cargotracker.quote.application.internal.queryservices.FindQuoteQueryService;
import com.example.cargotracker.quote.application.internal.queryservices.QuoteNotFoundException;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.interfaces.rest.dto.QuoteRequest;
import com.example.cargotracker.quote.interfaces.rest.dto.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/quotes")
@Tag(name = "quotes", description = "輸送見積 API")
public class QuoteRestController {

    private final RegisterQuoteCommandService registerQuoteCommandService;
    private final FindQuoteQueryService findQuoteQueryService;

    public QuoteRestController(RegisterQuoteCommandService registerQuoteCommandService,
                               FindQuoteQueryService findQuoteQueryService) {
        this.registerQuoteCommandService = registerQuoteCommandService;
        this.findQuoteQueryService = findQuoteQueryService;
    }

    @GetMapping
    @Operation(summary = "見積一覧取得", description = "登録済みの見積一覧を返す")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取得成功")
    })
    public List<QuoteResponse> list() {
        return findQuoteQueryService.findAll().stream()
                .map(QuoteResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "見積詳細取得", description = "指定 ID の見積を返す")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "取得成功"),
            @ApiResponse(responseCode = "404", description = "見積が見つからない")
    })
    public QuoteResponse detail(@PathVariable("id") String id) {
        return QuoteResponse.from(findQuote(id));
    }

    @PostMapping
    @Operation(summary = "見積登録", description = "新規見積を登録し、作成された見積を返す")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "登録成功"),
            @ApiResponse(responseCode = "400", description = "バリデーションエラー"),
            @ApiResponse(responseCode = "422", description = "利用可能なルートなし")
    })
    public ResponseEntity<QuoteResponse> register(@Valid @RequestBody QuoteRequest request,
                                                  UriComponentsBuilder uriComponentsBuilder) {
        Quote quote = registerQuoteCommandService.register(request.toCommand());
        URI location = uriComponentsBuilder.path("/api/v1/quotes/{id}")
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
