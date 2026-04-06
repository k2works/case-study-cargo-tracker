package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.CargoBookingQueryService;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.CargoResponse;
import com.example.cargotracker.booking.interfaces.rest.transform.CargoAssembler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingRestController {

    private final CargoBookingCommandService cargoBookingCommandService;
    private final CargoBookingQueryService cargoBookingQueryService;
    private final CargoAssembler cargoAssembler;

    public BookingRestController(
            CargoBookingCommandService cargoBookingCommandService,
            CargoBookingQueryService cargoBookingQueryService,
            CargoAssembler cargoAssembler
    ) {
        this.cargoBookingCommandService = cargoBookingCommandService;
        this.cargoBookingQueryService = cargoBookingQueryService;
        this.cargoAssembler = cargoAssembler;
    }

    @PostMapping
    public ResponseEntity<BookingIdResponse> create(@Valid @RequestBody BookCargoRequest request) {
        BookingId bookingId = cargoBookingCommandService.bookCargo(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(new BookingIdResponse(bookingId.toString()));
    }

    @GetMapping("/{bookingId}")
    public CargoResponse findByBookingId(@PathVariable String bookingId) {
        return cargoBookingQueryService.findByBookingId(bookingId)
                .map(cargoAssembler::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public java.util.List<CargoResponse> findAll() {
        return cargoBookingQueryService.findAll().stream()
                .map(cargoAssembler::toResponse)
                .toList();
    }

    @ExceptionHandler(ShipperNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShipperNotFound(ShipperNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SHIPPER_NOT_FOUND", "指定された荷主が見つかりません。"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    private BookCargoCommand toCommand(BookCargoRequest request) {
        return new BookCargoCommand(
                request.getShipperId(),
                request.getCargoType(),
                request.getWeight(),
                request.getDimensionLength(),
                request.getDimensionWidth(),
                request.getDimensionHeight(),
                request.getQuantity(),
                request.getDescription(),
                request.getOriginUnlocode(),
                request.getDestinationUnlocode(),
                request.getArrivalDeadline(),
                request.getHazardousClass(),
                request.getUnNumber(),
                request.getProperShippingName(),
                request.getMinTemperature(),
                request.getMaxTemperature(),
                request.getTemperatureUnit()
        );
    }

    private record BookingIdResponse(String bookingId) {
    }

    private record ErrorResponse(String code, String message) {
    }
}
