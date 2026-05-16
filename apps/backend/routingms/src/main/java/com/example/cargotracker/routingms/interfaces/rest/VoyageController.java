package com.example.cargotracker.routingms.interfaces.rest;

import com.example.cargotracker.routingms.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routingms.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageStatus;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routingms.infrastructure.persistence.VoyageRecord;
import com.example.cargotracker.routingms.interfaces.rest.dto.RegisterVoyageRequest;
import com.example.cargotracker.routingms.interfaces.rest.dto.UpdateVoyageScheduleRequest;
import com.example.cargotracker.routingms.interfaces.rest.dto.VoyageListResponse;
import com.example.cargotracker.routingms.interfaces.rest.dto.VoyageResponse;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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

    private static final String MESSAGE_KEY = "message";

    private final CommandGateway commandGateway;
    private final VoyageMapper voyageMapper;

    public VoyageController(CommandGateway commandGateway, VoyageMapper voyageMapper) {
        this.commandGateway = commandGateway;
        this.voyageMapper = voyageMapper;
    }

    @GetMapping
    public ResponseEntity<List<VoyageListResponse>> list(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTo,
            @RequestParam(required = false) String cargoType) {
        List<VoyageRecord> records = (origin == null && destination == null
                && departureFrom == null && departureTo == null && cargoType == null)
                ? voyageMapper.findAll()
                : voyageMapper.findByCriteria(origin, destination, departureFrom, departureTo, cargoType);
        return ResponseEntity.ok(records.stream().map(this::toListResponse).toList());
    }

    /**
     * US25: 単一の Voyage を取得（編集画面で既存値を表示するため）。
     * 存在しなければ 404。
     */
    @GetMapping("/{voyageNumber}")
    public ResponseEntity<Object> getOne(@PathVariable String voyageNumber) {
        VoyageRecord entity = voyageMapper.findByVoyageNumber(voyageNumber);
        if (entity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, "voyage_number が存在しません: " + voyageNumber));
        }
        return ResponseEntity.ok(toListResponse(entity));
    }

    private VoyageListResponse toListResponse(VoyageRecord entity) {
        return new VoyageListResponse(
                entity.getVoyageNumber(),
                entity.getCarrierCode(),
                entity.getCarrierName(),
                entity.getShipName(),
                entity.getOriginUnlocode(),
                entity.getDestinationUnlocode(),
                entity.getDepartureDate(),
                entity.getArrivalDate(),
                entity.getStatus());
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
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 2. 同一 voyage_number 重複チェック
        if (voyageMapper.existsByVoyageNumber(voyageNumber.value())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(MESSAGE_KEY, "voyage_number は既に存在します: " + voyageNumber.value()));
        }

        // 3. Axon CommandGateway 経由で送信
        commandGateway.sendAndWait(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new VoyageResponse(voyageNumber.value(), VoyageStatus.SCHEDULED.name()));
    }

    /**
     * US25: 既存航海スケジュール更新（UC19）。
     *
     * <p>差分確認は UI 側で実施し、本エンドポイントは確定済みの更新内容を受け取る。
     * 存在しない voyage_number は 404、入力不正は 400、成功時は 200 + 最新 Voyage を返す。</p>
     */
    @PutMapping("/{voyageNumber}")
    public ResponseEntity<Object> updateSchedule(
            @PathVariable String voyageNumber,
            @Valid @RequestBody UpdateVoyageScheduleRequest request) {
        // 1. 存在チェック
        if (!voyageMapper.existsByVoyageNumber(voyageNumber)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, "voyage_number が存在しません: " + voyageNumber));
        }

        // 2. Command 構築（VO の不変条件と連続性検証はここで例外）
        final UpdateVoyageScheduleCommand command;
        try {
            command = toUpdateCommand(voyageNumber, request);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 3. Axon CommandGateway 経由で送信
        commandGateway.sendAndWait(command);

        // 4. 更新後の最新 Read Model を返却（差分確認は UI 側で扱うが、API では確定値を返す）
        VoyageRecord updated = voyageMapper.findByVoyageNumber(voyageNumber);
        return ResponseEntity.ok(toListResponse(updated));
    }

    private UpdateVoyageScheduleCommand toUpdateCommand(String voyageNumber,
                                                        UpdateVoyageScheduleRequest request) {
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
        return new UpdateVoyageScheduleCommand(
                voyageNumber,
                request.departureDate(),
                request.arrivalDate(),
                movements,
                cargoTypes);
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
