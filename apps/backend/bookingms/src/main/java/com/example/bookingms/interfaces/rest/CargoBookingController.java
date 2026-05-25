package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.CargoCommandService;
import com.example.bookingms.application.CargoQueryService;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.domain.projections.CargoSummary;
import com.example.bookingms.interfaces.rest.dto.BookCargoRequest;
import com.example.bookingms.interfaces.rest.dto.CargoSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 貨物予約 REST Controller（US04）。
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
                        request.productName())
        );

        commandService.book(command).join();
        return ResponseEntity.status(201).body(Map.of("bookingId", bookingId));
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
    public ResponseEntity<List<CargoSummaryResponse>> findAll() {
        List<CargoSummaryResponse> list = queryService.findAll().stream()
                .map(CargoSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }
}
