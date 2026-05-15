package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageStatus;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import com.example.cargotracker.routingms.interfaces.rest.dto.RegisterVoyageRequest;
import com.example.cargotracker.routingms.interfaces.rest.dto.VoyageListResponse;
import com.example.cargotracker.routingms.interfaces.rest.dto.VoyageResponse;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 航海スケジュール REST API（US24）。
 *
 * <p>POST /api/v1/voyages: 重複チェック → RegisterVoyageCommand を Axon に送信 →
 * Voyage Aggregate が VoyageRegisteredEvent を発行 → VoyageProjectionsEventHandler
 * が routing_read_db に反映。</p>
 */
@RestController
@RequestMapping("/api/v1/voyages")
public class VoyageController {

    private final CommandGateway commandGateway;
    private final VoyageMapper voyageMapper;

    public VoyageController(CommandGateway commandGateway, VoyageMapper voyageMapper) {
        this.commandGateway = commandGateway;
        this.voyageMapper = voyageMapper;
    }

    @GetMapping
    public ResponseEntity<List<VoyageListResponse>> list() {
        List<VoyageListResponse> voyages = voyageMapper.findAll().stream()
                .map(this::toListResponse)
                .toList();
        return ResponseEntity.ok(voyages);
    }

    private VoyageListResponse toListResponse(VoyageRecord record) {
        return new VoyageListResponse(
                record.getVoyageNumber(),
                record.getCarrierCode(),
                record.getCarrierName(),
                record.getShipName(),
                record.getOriginUnlocode(),
                record.getDestinationUnlocode(),
                record.getDepartureDate(),
                record.getArrivalDate(),
                record.getStatus());
    }

    @PostMapping
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterVoyageRequest request) {
        // 1. voyage_number 形式チェックは VoyageNumber VO で実施（同時に IllegalArgumentException）
        final VoyageNumber voyageNumber;
        final RegisterVoyageCommand command;
        try {
            voyageNumber = new VoyageNumber(request.voyageNumber());
            command = toCommand(request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }

        // 2. 同一 voyage_number 重複チェック
        if (voyageMapper.existsByVoyageNumber(voyageNumber.value())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "voyage_number は既に存在します: " + voyageNumber.value()));
        }

        // 3. Axon CommandGateway 経由で送信
        commandGateway.sendAndWait(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new VoyageResponse(voyageNumber.value(), VoyageStatus.SCHEDULED.name()));
    }

    private RegisterVoyageCommand toCommand(RegisterVoyageRequest request) {
        var carrier = new Carrier(request.carrierCode(), request.carrierName());
        var origin = new UnLocode(request.originUnLocode());
        var destination = new UnLocode(request.destinationUnLocode());

        List<CarrierMovement> movements = request.carrierMovements().stream()
                .map(m -> new CarrierMovement(
                        new UnLocode(m.departureUnLocode()),
                        new UnLocode(m.arrivalUnLocode()),
                        m.departureTime(),
                        m.arrivalTime()))
                .toList();

        List<CargoType> cargoTypes = request.acceptedCargoTypes().stream()
                .map(CargoType::valueOf)
                .toList();

        return new RegisterVoyageCommand(
                request.voyageNumber(),
                carrier,
                request.shipName(),
                origin,
                destination,
                request.departureDate(),
                request.arrivalDate(),
                movements,
                cargoTypes);
    }
}
