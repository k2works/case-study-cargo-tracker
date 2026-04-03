package com.example.cargotracker.exception.interfaces.rest;

import com.example.cargotracker.exception.application.internal.commandservices.RecordCargoExceptionCommandService;
import com.example.cargotracker.exception.application.internal.commandservices.TrackingNotFoundException;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.exception.interfaces.rest.dto.CargoExceptionResponse;
import com.example.cargotracker.exception.interfaces.rest.dto.RecordCargoExceptionRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 貨物例外 REST API コントローラー。
 */
@RestController
@Validated
@RequestMapping("/api/v1/cargo-exceptions")
@Tag(name = "cargo-exceptions", description = "貨物例外 API — 遅延・破損・紛失の記録")
public class CargoExceptionRestController {

    private final RecordCargoExceptionCommandService recordCargoExceptionCommandService;

    public CargoExceptionRestController(RecordCargoExceptionCommandService recordCargoExceptionCommandService) {
        this.recordCargoExceptionCommandService = recordCargoExceptionCommandService;
    }

    @PostMapping
    @Operation(summary = "貨物例外記録",
               description = "追跡番号に対して遅延・破損・紛失の例外事象を記録する。追跡番号が存在しない場合は 404 を返す。")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "記録成功。Location ヘッダーに作成リソース URI を含む"),
        @ApiResponse(responseCode = "400", description = "バリデーションエラー"),
        @ApiResponse(responseCode = "404", description = "指定した追跡番号が存在しない")
    })
    public ResponseEntity<CargoExceptionResponse> createCargoException(
            @Valid @RequestBody RecordCargoExceptionRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        ExceptionId id = recordCargoExceptionCommandService.execute(request.toCommand());
        URI location = uriComponentsBuilder.path("/api/v1/cargo-exceptions/{id}")
                .buildAndExpand(id.value())
                .toUri();
        boolean urgent = request.exceptionType() == ExceptionType.LOSS;
        CargoExceptionResponse response = CargoExceptionResponse.from(id, request, urgent);
        return ResponseEntity.created(location).body(response);
    }

    @ExceptionHandler(TrackingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTrackingNotFound(TrackingNotFoundException e) {
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
