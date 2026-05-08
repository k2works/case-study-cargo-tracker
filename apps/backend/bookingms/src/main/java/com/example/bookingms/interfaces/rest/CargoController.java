package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.commandservices.CargoCommandService;
import com.example.bookingms.application.internal.commandservices.RouteCargoCommand;
import com.example.bookingms.application.internal.queryservices.CargoQueryService;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.interfaces.rest.dto.AssignRouteRequest;
import com.example.bookingms.interfaces.rest.dto.CargoResponse;
import com.example.bookingms.interfaces.rest.dto.CreateCargoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 貨物予約 REST コントローラー
 */
@RestController
@RequestMapping("/api/booking/v1/cargos")
public class CargoController {

    private final CargoCommandService cargoCommandService;
    private final CargoQueryService cargoQueryService;

    public CargoController(CargoCommandService cargoCommandService, CargoQueryService cargoQueryService) {
        this.cargoCommandService = cargoCommandService;
        this.cargoQueryService = cargoQueryService;
    }

    /**
     * 貨物予約を登録する
     */
    @PostMapping
    public ResponseEntity<CargoResponse> createCargo(@RequestBody CreateCargoRequest request) {
        Cargo cargo = cargoCommandService.registerBooking(
                request.shipperId(),
                request.cargoType(),
                request.weightKg(),
                request.originUnlocode(),
                request.destinationUnlocode(),
                request.arrivalDeadline()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CargoResponse.from(cargo));
    }

    /**
     * 貨物一覧を取得する
     */
    @GetMapping
    public ResponseEntity<List<CargoResponse>> listCargos() {
        List<CargoResponse> responses = cargoQueryService.findAll().stream()
                .map(CargoResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * 予約 ID で貨物を取得する
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<CargoResponse> getCargo(@PathVariable String bookingId) {
        return cargoQueryService.findByBookingId(bookingId)
                .map(CargoResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 経路を割り当てる（RouteCargoCommand）
     */
    @PutMapping("/{bookingId}/route")
    public ResponseEntity<CargoResponse> assignRoute(
            @PathVariable String bookingId,
            @RequestBody AssignRouteRequest request) {
        try {
            RouteCargoCommand command = new RouteCargoCommand(
                    request.legs().stream()
                            .map(l -> new RouteCargoCommand.LegData(
                                    l.voyageNumber(),
                                    l.loadLocationUnlocode(),
                                    l.unloadLocationUnlocode(),
                                    l.loadTime(),
                                    l.unloadTime()))
                            .toList());
            Cargo cargo = cargoCommandService.assignRoute(bookingId, command);
            return ResponseEntity.ok(CargoResponse.from(cargo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 予約を確定する（UpdateBookingStatusCommand）
     */
    @PutMapping("/{bookingId}/confirm")
    public ResponseEntity<CargoResponse> confirmBooking(@PathVariable String bookingId) {
        try {
            Cargo cargo = cargoCommandService.confirmBooking(bookingId);
            return ResponseEntity.ok(CargoResponse.from(cargo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 予約をキャンセルする
     */
    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<CargoResponse> cancelBooking(@PathVariable String bookingId) {
        try {
            Cargo cargo = cargoCommandService.cancelBooking(bookingId);
            return ResponseEntity.ok(CargoResponse.from(cargo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
