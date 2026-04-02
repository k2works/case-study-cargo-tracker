package com.example.cargotracker.handling.interfaces.rest;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
import com.example.cargotracker.handling.application.internal.queryservices.FindHandlingEventsQueryService;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.interfaces.rest.dto.HandlingEventResponse;
import com.example.cargotracker.handling.interfaces.rest.dto.RecordHandlingEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/handling-events")
public class HandlingRestController {

    private final RecordHandlingEventCommandService recordHandlingEventCommandService;
    private final FindHandlingEventsQueryService findHandlingEventsQueryService;

    public HandlingRestController(RecordHandlingEventCommandService recordHandlingEventCommandService,
                                   FindHandlingEventsQueryService findHandlingEventsQueryService) {
        this.recordHandlingEventCommandService = recordHandlingEventCommandService;
        this.findHandlingEventsQueryService = findHandlingEventsQueryService;
    }

    @PostMapping
    public ResponseEntity<HandlingEventResponse> createHandlingEvent(
            @Valid @RequestBody RecordHandlingEventRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        HandlingEventId id = recordHandlingEventCommandService.execute(request.toCommand());
        URI location = uriComponentsBuilder.path("/api/v1/handling-events/{id}")
                .buildAndExpand(id)
                .toUri();
        // 記録直後に再取得はしない。登録情報をそのままレスポンスとして返す
        HandlingEventResponse response = new HandlingEventResponse(
                id.value(),
                request.bookingId(),
                request.eventType(),
                request.locationCode(),
                request.completionTime(),
                request.memo()
        );
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<HandlingEventResponse> listByBookingId(@RequestParam("bookingId") UUID bookingId) {
        return findHandlingEventsQueryService.findByBookingId(bookingId).stream()
                .map(HandlingEventResponse::from)
                .toList();
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFound(BookingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problemDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(problemDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
