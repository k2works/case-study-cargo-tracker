package com.example.cargotracker.bookingms.interfaces.rest;

import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.domain.model.valueobjects.TemperatureCondition;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import com.example.cargotracker.bookingms.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.bookingms.interfaces.rest.dto.BookingResponse;
import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final CommandGateway commandGateway;
    private final ShipperRepository shipperRepository;

    public BookingController(CommandGateway commandGateway, ShipperRepository shipperRepository) {
        this.commandGateway = commandGateway;
        this.shipperRepository = shipperRepository;
    }

    @PostMapping
    public ResponseEntity<Object> book(@Valid @RequestBody BookCargoRequest request) {
        // 1. 荷主存在チェック
        if (!shipperRepository.existsById(request.shipperId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "指定された荷主 ID が存在しません: " + request.shipperId()));
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
                    .body(Map.of("message", e.getMessage()));
        }

        // 3. Axon CommandGateway 経由で Cargo Aggregate に送信
        commandGateway.sendAndWait(command);

        // 4. レスポンス（PRELIMINARY は Cargo Aggregate のイベント処理直後の規定状態）
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BookingResponse(bookingId.value(), BookingStatus.PRELIMINARY.name()));
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
