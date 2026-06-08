package com.example.routingms.interfaces.rest;

import com.example.routingms.application.RouteCalculationService;
import com.example.routingms.application.RouteConfirmationService;
import com.example.routingms.application.RouteSelectionService;
import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.interfaces.rest.dto.CalculateRoutesRequest;
import com.example.routingms.interfaces.rest.dto.RouteCandidateResponse;
import com.example.routingms.interfaces.rest.dto.SelectRouteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 経路設計 REST Controller（US08 経路候補算出）。
 *
 * <p>経路設計者ワークベンチ（S14）が経路設計待ちリストの予約 ID を指定して、経路候補の算出（US08）・
 * 選択確定（US09）・予約紐付け（US11）を行う。経路候補は永続化せず算出のたびに返すため、選択・確定は
 * 推奨順の候補番号（sequence）で指定する（iteration_plan-4.md）。</p>
 *
 * <p>認可（IT10 A1.1 / US30）: クラスレベル {@code @PreAuthorize("hasAnyRole('ROUTING', 'ADMIN')")}
 * を付与し、URL ルール認可（{@link com.example.routingms.config.HerokuSecurityConfig}）と
 * 二段重層の深層防御を確立する。</p>
 */
@RestController
@RequestMapping("/api/v1/routes")
@PreAuthorize("hasAnyRole('ROUTING', 'ADMIN')")
public class RouteController {

    private final RouteCalculationService calculationService;
    private final RouteSelectionService selectionService;
    private final RouteConfirmationService confirmationService;

    public RouteController(RouteCalculationService calculationService,
                           RouteSelectionService selectionService,
                           RouteConfirmationService confirmationService) {
        this.calculationService = calculationService;
        this.selectionService = selectionService;
        this.confirmationService = confirmationService;
    }

    /**
     * 経路候補を算出する（US08）。期限内に到達可能な候補がない場合は空リストを返す。
     * リクエストボディで {@code arrivalDeadline} を指定すると、その期限で再算出する（US10 条件調整）。
     */
    @PostMapping("/{bookingId}/calculate")
    public ResponseEntity<List<RouteCandidateResponse>> calculate(
            @PathVariable String bookingId,
            @RequestBody(required = false) CalculateRoutesRequest request) {
        List<RouteCandidate> candidates = (request != null && request.arrivalDeadline() != null)
                ? calculationService.calculate(bookingId, request.arrivalDeadline())
                : calculationService.calculate(bookingId);
        return ResponseEntity.ok(toResponses(candidates));
    }

    /**
     * 算出済みの経路候補を取得する（US08 / US09）。現状は算出と同じ結果を返す。
     */
    @GetMapping("/{bookingId}/candidates")
    public ResponseEntity<List<RouteCandidateResponse>> candidates(@PathVariable String bookingId) {
        return ResponseEntity.ok(toResponses(calculationService.calculate(bookingId)));
    }

    /**
     * 経路候補を選択して確定する（US09）。選択した候補を返し、経路設計依頼を選択済みにする。
     */
    @PostMapping("/{bookingId}/select")
    public ResponseEntity<RouteCandidateResponse> select(@PathVariable String bookingId,
                                                         @RequestBody SelectRouteRequest request) {
        RouteCandidate selected = selectionService.selectRoute(bookingId, request.sequence());
        return ResponseEntity.ok(RouteCandidateResponse.from(selected, request.sequence(), false));
    }

    /**
     * 確定経路を予約に紐付ける（US11）。RouteConfirmedEvent を発行して bookingms へ cross-service 連携する。
     */
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<RouteCandidateResponse> confirm(@PathVariable String bookingId,
                                                          @RequestBody SelectRouteRequest request) {
        RouteCandidate confirmed = confirmationService.confirmRoute(bookingId, request.sequence());
        return ResponseEntity.accepted().body(RouteCandidateResponse.from(confirmed, request.sequence(), false));
    }

    private List<RouteCandidateResponse> toResponses(List<RouteCandidate> candidates) {
        return IntStream.range(0, candidates.size())
                .mapToObj(i -> RouteCandidateResponse.from(candidates.get(i), i + 1, i == 0))
                .toList();
    }
}
