package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.application.RouteApplicationService;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.interfaces.rest.dto.AdjustRouteRequest;
import com.example.cargotracker.routingms.interfaces.rest.dto.AdjustRouteResponse;
import com.example.cargotracker.routingms.interfaces.rest.dto.RouteCandidatesResponse;
import com.example.cargotracker.routingms.interfaces.rest.dto.SelectRouteRequest;
import com.example.cargotracker.routingms.interfaces.rest.dto.SelectedRouteResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 経路算出 REST API（US08）。
 */
@RestController
@RequestMapping("/api/v1/routing")
public class RouteController {

    private final RouteApplicationService routeService;

    public RouteController(RouteApplicationService routeService) {
        this.routeService = routeService;
    }

    /**
     * GET /api/v1/routing/candidates
     *
     * <p>US08: 出発地・目的地・期限・貨物種別を条件に経路候補を算出して返す。</p>
     */
    @GetMapping("/candidates")
    public RouteCandidatesResponse getCandidates(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrivalDeadline,
            @RequestParam CargoType cargoType) {
        return routeService.findCandidates(origin, destination, arrivalDeadline, cargoType);
    }

    /**
     * POST /api/v1/routing/adjust
     *
     * <p>US10: 経路条件を調整して再算出し、候補一覧を返す。候補ゼロ時は営業担当者向けメッセージを返す。</p>
     */
    @PostMapping("/adjust")
    public AdjustRouteResponse adjustRoute(@RequestBody AdjustRouteRequest request) {
        return routeService.adjustRoute(
                request.origin(),
                request.destination(),
                request.arrivalDeadline(),
                CargoType.valueOf(request.cargoType()),
                request.excludePorts());
    }

    /**
     * POST /api/v1/routing/select
     *
     * <p>US09: 候補インデックスで経路を選択し、選択した経路の Legs を返す。</p>
     */
    @PostMapping("/select")
    public SelectedRouteResponse selectRoute(@RequestBody SelectRouteRequest request) {
        return routeService.selectRoute(
                request.origin(),
                request.destination(),
                request.arrivalDeadline(),
                CargoType.valueOf(request.cargoType()),
                request.selectedIndex());
    }
}
