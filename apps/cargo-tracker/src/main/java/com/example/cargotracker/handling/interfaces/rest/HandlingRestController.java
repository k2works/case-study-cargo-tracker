package com.example.cargotracker.handling.interfaces.rest;

import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.commandservices.RecordHandlingEventCommandService;
import com.example.cargotracker.handling.domain.model.exceptions.DuplicateReceiveException;
import com.example.cargotracker.handling.application.internal.queryservices.FindHandlingEventsQueryService;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.interfaces.rest.dto.HandlingEventResponse;
import com.example.cargotracker.handling.interfaces.rest.dto.RecordHandlingEventRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 荷役イベント REST API コントローラー。
 *
 * <p>荷役イベントの記録（POST）と予約 ID による一覧取得（GET）を提供する。
 * 予約 BC への存在確認は {@link com.example.cargotracker.handling.application.internal.ports.BookingExistencePort} を通じて行う。
 */
@RestController
@Validated
@RequestMapping("/api/v1/handling-events")
@Tag(name = "handling-events", description = "荷役イベント API — 荷役作業の記録と予約単位での照会")
public class HandlingRestController {

    private final RecordHandlingEventCommandService recordHandlingEventCommandService;
    private final FindHandlingEventsQueryService findHandlingEventsQueryService;

    public HandlingRestController(RecordHandlingEventCommandService recordHandlingEventCommandService,
                                   FindHandlingEventsQueryService findHandlingEventsQueryService) {
        this.recordHandlingEventCommandService = recordHandlingEventCommandService;
        this.findHandlingEventsQueryService = findHandlingEventsQueryService;
    }

    @PostMapping
    @Operation(summary = "荷役イベント記録",
               description = "港湾・輸送における荷役作業を記録する。bookingId に対応する予約が存在しない場合は 404 を返す。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "記録成功。Location ヘッダーに作成リソース URI を含む"),
        @ApiResponse(responseCode = "400", description = "バリデーションエラー"),
        @ApiResponse(responseCode = "404", description = "指定した bookingId の予約が存在しない"),
        @ApiResponse(responseCode = "409", description = "同一予約に RECEIVE イベントが既に存在する（RECEIVE は 1 回のみ登録可能）")
    })
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
    @Operation(summary = "荷役イベント一覧取得（予約 ID フィルター）",
               description = "bookingId は必須。対応する荷役イベントが存在しない場合は空配列を返す（200）。")
    @ApiResponse(responseCode = "200", description = "取得成功（0 件でも 200）")
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

    @ExceptionHandler(DuplicateReceiveException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateReceive(DuplicateReceiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(problemDetail(HttpStatus.CONFLICT, e.getMessage()));
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
