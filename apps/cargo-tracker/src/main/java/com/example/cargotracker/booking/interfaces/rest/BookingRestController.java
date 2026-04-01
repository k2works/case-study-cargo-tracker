package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.internal.commandservices.RegisterBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ShipperNotFoundException;
import com.example.cargotracker.booking.application.internal.queryservices.BookingNotFoundException;
import com.example.cargotracker.booking.application.internal.queryservices.FindBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingRequest;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/bookings")
public class BookingRestController {

    private final RegisterBookingCommandService registerBookingCommandService;
    private final FindBookingQueryService findBookingQueryService;

    public BookingRestController(RegisterBookingCommandService registerBookingCommandService,
                                 FindBookingQueryService findBookingQueryService) {
        this.registerBookingCommandService = registerBookingCommandService;
        this.findBookingQueryService = findBookingQueryService;
    }

    @GetMapping
    public List<BookingResponse> list() {
        return findBookingQueryService.findAll().stream()
                .map(BookingResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BookingResponse detail(@PathVariable("id") String id) {
        return BookingResponse.from(findBooking(id));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> register(@Valid @RequestBody BookingRequest request,
                                                    UriComponentsBuilder uriComponentsBuilder) {
        var bookingId = registerBookingCommandService.execute(request.toCommand());
        Booking booking = findBooking(bookingId.toString());
        URI location = uriComponentsBuilder.path("/api/bookings/{id}")
                .buildAndExpand(bookingId)
                .toUri();
        return ResponseEntity.created(location).body(BookingResponse.from(booking));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBookingNotFound(BookingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problemDetail(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler({ShipperNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(problemDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private Booking findBooking(String id) {
        try {
            return findBookingQueryService.execute(new BookingId(UUID.fromString(id)));
        } catch (IllegalArgumentException _) {
            throw new BookingNotFoundException(id);
        }
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
