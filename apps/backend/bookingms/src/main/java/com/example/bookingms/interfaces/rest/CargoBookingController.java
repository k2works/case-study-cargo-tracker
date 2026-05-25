package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.CargoCommandService;
import com.example.bookingms.application.CargoQueryService;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.HazardInfo;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.model.TemperatureCondition;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.interfaces.rest.dto.BookCargoRequest;
import com.example.bookingms.interfaces.rest.dto.CargoSummaryResponse;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import com.example.bookingms.interfaces.rest.dto.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 貨物予約 REST Controller（US04 + US05）。
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class CargoBookingController {

    private final CargoCommandService commandService;
    private final CargoQueryService queryService;

    public CargoBookingController(CargoCommandService commandService, CargoQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> book(@RequestBody BookCargoRequest request) {
        String bookingId = request.bookingId() == null || request.bookingId().isBlank()
                ? UUID.randomUUID().toString()
                : request.bookingId();

        BookCargoCommand command = new BookCargoCommand(
                bookingId,
                request.shipperId(),
                new RouteSpecification(
                        request.originUnlocode(),
                        request.destinationUnlocode(),
                        request.arrivalDeadline()),
                new CargoSpecification(
                        CargoType.valueOf(request.cargoType()),
                        request.weightKg(),
                        new Dimensions(
                                request.lengthCm() == null ? 0 : request.lengthCm(),
                                request.widthCm() == null ? 0 : request.widthCm(),
                                request.heightCm() == null ? 0 : request.heightCm()),
                        request.quantity() == null ? 0 : request.quantity(),
                        request.productName(),
                        toHazardInfo(request),
                        toTemperatureCondition(request))
        );

        commandService.book(command).join();
        return ResponseEntity.status(201).body(Map.of("bookingId", bookingId));
    }

    private HazardInfo toHazardInfo(BookCargoRequest request) {
        if (request.hazardImoClass() == null
                && request.hazardUnNumber() == null
                && request.hazardDeclaration() == null) {
            return null;
        }
        return new HazardInfo(
                request.hazardImoClass(),
                request.hazardUnNumber(),
                request.hazardDeclaration());
    }

    private TemperatureCondition toTemperatureCondition(BookCargoRequest request) {
        BigDecimal min = request.temperatureMinC();
        BigDecimal max = request.temperatureMaxC();
        if (min == null && max == null) {
            return null;
        }
        return new TemperatureCondition(min, max);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<CargoSummaryResponse> findByBookingId(@PathVariable String bookingId) {
        CargoSummary projection = queryService.findByBookingId(bookingId);
        if (projection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(CargoSummaryResponse.from(projection));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CargoSummaryResponse>> findAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        PageRequest pageRequest = new PageRequest(page, size);
        List<CargoSummaryResponse> items = queryService.findAll(pageRequest).stream()
                .map(CargoSummaryResponse::from)
                .toList();
        long totalCount = queryService.count();
        return ResponseEntity.ok(PageResponse.of(items, totalCount, pageRequest.page(), pageRequest.size()));
    }
}
