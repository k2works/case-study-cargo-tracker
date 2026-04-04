package com.example.cargotracker.routing.interfaces.rest;

import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.interfaces.rest.dto.RouteDesignConditionResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.RoutingCandidateResponse;
import com.example.cargotracker.routing.interfaces.rest.dto.VoyageScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ルート検索 REST API コントローラー。
 *
 * <p>product プロファイルでは {@link RouteSearchService} が未登録のため
 * {@code Optional<RouteSearchService>} で注入し、未設定時は 503 を返す。
 */
@RestController
@RequestMapping("/api/v1/routings")
@Tag(name = "routings", description = "ルート検索 API")
public class RoutingRestController {

    private final Optional<RouteSearchService> routeSearchService;
    private final RouteDesignConditionQueryService routeDesignConditionQueryService;
    private final VoyageScheduleSearchService voyageScheduleSearchService;

    public RoutingRestController(
            Optional<RouteSearchService> routeSearchService,
            RouteDesignConditionQueryService routeDesignConditionQueryService,
            VoyageScheduleSearchService voyageScheduleSearchService) {
        this.routeSearchService = routeSearchService;
        this.routeDesignConditionQueryService = routeDesignConditionQueryService;
        this.voyageScheduleSearchService = voyageScheduleSearchService;
    }

    @GetMapping("/design-condition")
    @Operation(
            summary = "経路設計条件の取得",
            description = "指定した予約の経路設計条件（出発地・目的地・期限・貨物種別）を返す",
            responses = {
                    @ApiResponse(responseCode = "200", description = "取得成功"),
                    @ApiResponse(responseCode = "400", description = "bookingId パラメータなし"),
                    @ApiResponse(responseCode = "404", description = "予約が見つからない")
            }
    )
    public ResponseEntity<RouteDesignConditionResponse> getDesignCondition(
            @RequestParam("bookingId") UUID bookingId) {
        var condition = routeDesignConditionQueryService.findByBookingId(bookingId);
        return ResponseEntity.ok(RouteDesignConditionResponse.from(condition));
    }

    @GetMapping("/voyage-schedules")
    @Operation(
            summary = "航海スケジュール検索",
            description = "出発地・目的地・配達期限を条件として航海スケジュールを検索する",
            responses = {
                    @ApiResponse(responseCode = "200", description = "取得成功"),
                    @ApiResponse(responseCode = "400", description = "必須パラメータなし")
            }
    )
    public ResponseEntity<List<VoyageScheduleResponse>> searchVoyageSchedules(
            @RequestParam("origin") String origin,
            @RequestParam("dest") String dest,
            @RequestParam(value = "deadline", required = false) LocalDate deadline) {
        List<VoyageScheduleResponse> results = voyageScheduleSearchService.search(origin, dest, deadline)
            .stream()
            .map(VoyageScheduleResponse::from)
            .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search")
    @Operation(
            summary = "予約 ID によるルート候補検索",
            description = "指定した予約の輸送条件に合うルート候補を返す",
            responses = {
                    @ApiResponse(responseCode = "200", description = "取得成功"),
                    @ApiResponse(responseCode = "400", description = "bookingId パラメータなし"),
                    @ApiResponse(responseCode = "404", description = "予約が見つからない"),
                    @ApiResponse(responseCode = "503", description = "ルート検索サービスが利用できない")
            }
    )
    public ResponseEntity<List<RoutingCandidateResponse>> search(@RequestParam("bookingId") UUID bookingId) {
        if (routeSearchService.isEmpty()) {
            throw new RouteSearchServiceUnavailableException();
        }

        List<RoutingCandidateResponse> candidates = routeSearchService.get()
                .searchByBookingId(bookingId)
                .stream()
                .map(RoutingCandidateResponse::from)
                .toList();
        return ResponseEntity.ok(candidates);
    }

    @ExceptionHandler(BookingDataNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFound(BookingDataNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(RouteSearchServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(RouteSearchServiceUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    private static final class RouteSearchServiceUnavailableException extends RuntimeException {
        private RouteSearchServiceUnavailableException() {
            super("ルート検索サービスは現在利用できません");
        }
    }
}
