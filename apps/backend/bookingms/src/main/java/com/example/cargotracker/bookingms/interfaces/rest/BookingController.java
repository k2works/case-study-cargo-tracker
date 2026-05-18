package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.AssignRouteToCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.HandOffToRoutingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.IssueTrackingNumberCommand;
import com.example.cargotracker.bookingms.domain.model.commands.NotifyRouteCommand;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Leg;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.TemperatureCondition;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.CargoSummaryRecord;
import com.example.cargotracker.bookingms.interfaces.rest.dto.AssignRouteRequest;
import com.example.cargotracker.bookingms.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.bookingms.interfaces.rest.dto.BookingListResponse;
import com.example.cargotracker.bookingms.interfaces.rest.dto.BookingResponse;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 貨物予約 REST API（US04）。
 *
 * <p>POST /api/v1/bookings: 荷主存在チェック → BookCargoCommand を Axon に送信 →
 * Cargo Aggregate が CargoBookedEvent を発行 → CargoProjectionsEventHandler が
 * cargo_summary に反映。</p>
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final String MESSAGE_KEY = "message";

    private final CommandGateway commandGateway;
    private final ShipperRepository shipperRepository;
    private final CargoSummaryMapper cargoSummaryMapper;

    public BookingController(CommandGateway commandGateway,
                             ShipperRepository shipperRepository,
                             CargoSummaryMapper cargoSummaryMapper) {
        this.commandGateway = commandGateway;
        this.shipperRepository = shipperRepository;
        this.cargoSummaryMapper = cargoSummaryMapper;
    }

    @GetMapping
    public ResponseEntity<List<BookingListResponse>> list(
            @RequestParam(value = "status", required = false) String status) {
        List<CargoSummaryRecord> records;
        if (status == null || status.isBlank()) {
            records = cargoSummaryMapper.findAll();
        } else {
            // バリデーション: BookingStatus 列挙値以外は 400
            try {
                BookingStatus.valueOf(status);
            } catch (IllegalArgumentException _) {
                return ResponseEntity.badRequest().build();
            }
            records = cargoSummaryMapper.findByBookingStatus(status);
        }
        List<BookingListResponse> bookings = records.stream()
                .map(this::toListResponse)
                .toList();
        return ResponseEntity.ok(bookings);
    }

    private BookingListResponse toListResponse(CargoSummaryRecord summary) {
        return new BookingListResponse(
                summary.getBookingId(),
                summary.getShipperId(),
                summary.getTrackingNumber(),
                summary.getCargoType(),
                summary.getProductName(),
                summary.getOriginUnlocode(),
                summary.getDestinationUnlocode(),
                summary.getArrivalDeadline(),
                summary.getBookingStatus(),
                summary.getRoutingStatus(),
                summary.getCreatedAt());
    }

    @PostMapping
    public ResponseEntity<Object> book(@Valid @RequestBody BookCargoRequest request) {
        // 1. 荷主存在チェック
        if (!shipperRepository.existsById(request.shipperId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, "指定された荷主 ID が存在しません: " + request.shipperId()));
        }

        // 2. ドメイン値オブジェクトへ変換（バリデーション付き）
        final BookingId bookingId;
        final BookCargoCommand command;
        try {
            bookingId = BookingId.generate();
            command = new BookCargoCommand(
                    bookingId.value(),
                    new ShipperId(request.shipperId()),
                    toCargoSpecification(request.cargoSpec()),
                    toRouteSpecification(request.routeSpec()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }

        // 3. Axon CommandGateway 経由で Cargo Aggregate に送信
        commandGateway.sendAndWait(command);

        // 4. レスポンス（PRELIMINARY は Cargo Aggregate のイベント処理直後の規定状態）
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BookingResponse(bookingId.value(), BookingStatus.PRELIMINARY.name()));
    }

    /**
     * 経路設計引き渡し（US06 / UC04）。
     *
     * <p>仮受付状態の予約を経路設計者に引き渡し、状態を {@code ROUTING} に遷移させる。</p>
     */
    @PostMapping("/{bookingId}/handoff")
    public ResponseEntity<Object> handoff(@PathVariable("bookingId") String bookingId) {
        var summary = cargoSummaryMapper.findByBookingId(bookingId);
        if (summary.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(MESSAGE_KEY, "予約が存在しません: " + bookingId));
        }
        try {
            commandGateway.sendAndWait(new HandOffToRoutingCommand(bookingId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(MESSAGE_KEY, e.getMessage()));
        }
        return ResponseEntity.ok(new BookingResponse(bookingId, BookingStatus.ROUTING.name()));
    }

    /**
     * 経路紐付け（US11 / UC09）。
     *
     * <p>確定経路を予約に紐付け、{@code BookingStatus.ROUTE_PROPOSED} に遷移させる。</p>
     */
    @PostMapping("/{bookingId}/assign-route")
    public ResponseEntity<Object> assignRoute(
            @PathVariable("bookingId") String bookingId,
            @RequestBody AssignRouteRequest request) {
        var legs = request.legs().stream()
                .map(dto -> new Leg(
                        dto.voyageNumber(),
                        Location.of(dto.loadUnlocode()),
                        Location.of(dto.unloadUnlocode()),
                        dto.loadTime(),
                        dto.unloadTime()))
                .toList();
        var itinerary = new CargoItinerary(legs);
        commandGateway.send(new AssignRouteToCargoCommand(bookingId, itinerary));
        return ResponseEntity.ok(new BookingResponse(bookingId, BookingStatus.ROUTE_PROPOSED.name()));
    }

    /**
     * 荷主への経路通知（US12 / UC10）。
     *
     * <p>IT4 はログ記録のみ。メール送信は IT5+ で実装予定。</p>
     */
    @PostMapping("/{bookingId}/notify-route")
    public ResponseEntity<Object> notifyRoute(@PathVariable String bookingId) {
        commandGateway.send(new NotifyRouteCommand(bookingId));
        return ResponseEntity.ok(new BookingResponse(bookingId, null));
    }

    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<Object> confirmBooking(@PathVariable String bookingId) {
        commandGateway.send(new ConfirmBookingCommand(bookingId));
        return ResponseEntity.ok(new BookingResponse(bookingId, BookingStatus.CONFIRMED.name()));
    }

    @PostMapping("/{bookingId}/issue-tracking")
    public ResponseEntity<Object> issueTrackingNumber(@PathVariable String bookingId) {
        commandGateway.send(new IssueTrackingNumberCommand(bookingId));
        return ResponseEntity.ok(new BookingResponse(bookingId, BookingStatus.TRACKING_ISSUED.name()));
    }

    private CargoSpecification toCargoSpecification(BookCargoRequest.CargoSpecDto dto) {
        var dimDto = dto.dimensions();
        var dim = dimDto == null
                ? new Dimensions(0, 0, 0)
                : new Dimensions(dimDto.lengthCm(), dimDto.widthCm(), dimDto.heightCm());
        HazardInfo hazard = dto.hazardInfo() == null ? null
                : new HazardInfo(dto.hazardInfo().imoClass(), dto.hazardInfo().unNumber(),
                        dto.hazardInfo().declaration());
        TemperatureCondition temp = dto.temperatureCondition() == null ? null
                : new TemperatureCondition(dto.temperatureCondition().minCelsius(),
                        dto.temperatureCondition().maxCelsius());
        return new CargoSpecification(
                CargoType.valueOf(dto.cargoType()),
                dto.weightKg(),
                dim,
                dto.quantity(),
                dto.productName(),
                hazard,
                temp);
    }

    private RouteSpecification toRouteSpecification(BookCargoRequest.RouteSpecDto dto) {
        return new RouteSpecification(
                Location.of(dto.originUnLocode()),
                Location.of(dto.destinationUnLocode()),
                dto.arrivalDeadline());
    }
}
