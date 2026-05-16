package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationCandidateRecord;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationRecord;
import com.example.cargotracker.bookingms.interfaces.rest.dto.CreateQuotationRequest;
import com.example.cargotracker.bookingms.interfaces.rest.dto.CreateQuotationResponse;
import com.example.cargotracker.bookingms.interfaces.rest.dto.QuotationListResponse;
import com.example.cargotracker.bookingms.interfaces.rest.dto.QuotationResponse;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 見積 REST API（US01 / UC01）。
 *
 * <p>POST /api/v1/quotations: 荷主存在チェック → CreateQuotationCommand を Axon に送信
 * → Quotation Aggregate が QuotationCreatedEvent を発行 → QuotationProjectionsEventHandler
 * が quotation / quotation_candidate に投影。</p>
 *
 * <p>GET /api/v1/quotations/{quotationId}: Read Model から見積詳細と候補一覧を取得。</p>
 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private static final String MESSAGE_KEY = "message";

    private final CommandGateway commandGateway;
    private final ShipperRepository shipperRepository;
    private final QuotationMapper quotationMapper;

    public QuotationController(CommandGateway commandGateway,
                               ShipperRepository shipperRepository,
                               QuotationMapper quotationMapper) {
        this.commandGateway = commandGateway;
        this.shipperRepository = shipperRepository;
        this.quotationMapper = quotationMapper;
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody CreateQuotationRequest request) {
        // 1. 荷主存在チェック
        if (!shipperRepository.existsById(request.shipperId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, "指定された荷主 ID が存在しません: " + request.shipperId()));
        }

        // 2. Command 構築（VO の不変条件と整合性検証）
        final QuotationId quotationId;
        final CreateQuotationCommand command;
        try {
            quotationId = QuotationId.generate();
            command = new CreateQuotationCommand(
                    quotationId.value(),
                    new ShipperId(request.shipperId()),
                    new RouteSpecification(
                            Location.of(request.originUnLocode()),
                            Location.of(request.destinationUnLocode()),
                            request.arrivalDeadline()),
                    CargoType.valueOf(request.cargoType()),
                    request.weightKg(),
                    toHazardInfo(request.hazardInfo()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 3. Axon CommandGateway 経由で送信
        commandGateway.sendAndWait(command);

        // 4. 状態は Projection 反映後に確定するため、レスポンスは作成時の見積 ID と
        //    暫定 status を返す。詳細は GET で取得する。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateQuotationResponse(quotationId.value(), "CREATED"));
    }

    /**
     * S02 見積一覧（US01）。作成日時の降順で返す。
     * 受入条件 4: 見積番号と概算料金を一覧で確認可能。
     */
    @GetMapping
    public ResponseEntity<List<QuotationListResponse>> list() {
        List<QuotationListResponse> quotations = quotationMapper.findAll().stream()
                .map(this::toListResponse)
                .toList();
        return ResponseEntity.ok(quotations);
    }

    @GetMapping("/{quotationId}")
    public ResponseEntity<Object> getOne(@PathVariable String quotationId) {
        QuotationRecord quotation = quotationMapper.findByQuotationId(quotationId);
        if (quotation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, "quotation_id が存在しません: " + quotationId));
        }
        List<QuotationCandidateRecord> candidates =
                quotationMapper.findCandidatesByQuotationId(quotationId);
        return ResponseEntity.ok(toResponse(quotation, candidates));
    }

    private QuotationListResponse toListResponse(QuotationRecord q) {
        return new QuotationListResponse(
                q.getQuotationId(),
                q.getShipperId(),
                q.getOriginUnlocode(),
                q.getDestinationUnlocode(),
                q.getArrivalDeadline(),
                q.getCargoType(),
                q.getWeightKg(),
                q.getEstimatedAmount(),
                q.getEstimatedCurrency(),
                q.getValidUntil(),
                q.getStatus());
    }

    private HazardInfo toHazardInfo(CreateQuotationRequest.HazardInfoDto dto) {
        if (dto == null) {
            return null;
        }
        return new HazardInfo(dto.imoClass(), dto.unNumber(), dto.declaration());
    }

    private QuotationResponse toResponse(QuotationRecord q, List<QuotationCandidateRecord> cs) {
        List<QuotationResponse.RouteCandidateDto> candidates = cs.stream()
                .map(c -> new QuotationResponse.RouteCandidateDto(
                        c.getCandidateSeq(),
                        c.getEstimatedDays(),
                        c.getEstimatedCost(),
                        c.getEstimatedCurrency(),
                        c.getItinerarySummary(),
                        c.getVoyageNumbers()))
                .toList();
        return new QuotationResponse(
                q.getQuotationId(),
                q.getShipperId(),
                q.getOriginUnlocode(),
                q.getDestinationUnlocode(),
                q.getArrivalDeadline(),
                q.getCargoType(),
                q.getWeightKg(),
                q.getEstimatedAmount(),
                q.getEstimatedCurrency(),
                q.getValidUntil(),
                q.getStatus(),
                q.getHazardImoClass(),
                q.getHazardUnNumber(),
                q.getHazardDeclaration(),
                candidates);
    }
}
