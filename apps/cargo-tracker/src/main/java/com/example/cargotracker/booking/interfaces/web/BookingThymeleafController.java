package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.CargoBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.transform.CargoAssembler;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/bookings")
public class BookingThymeleafController {

    private static final String BOOKING_ATTRIBUTE = "booking";
    private static final String CARGO_TYPES_ATTRIBUTE = "cargoTypes";
    private static final String NEW_VIEW = "booking/new";

    private final CargoBookingCommandService cargoBookingCommandService;
    private final CargoBookingQueryService cargoBookingQueryService;
    private final CargoAssembler cargoAssembler;

    public BookingThymeleafController(
            CargoBookingCommandService cargoBookingCommandService,
            CargoBookingQueryService cargoBookingQueryService,
            CargoAssembler cargoAssembler
    ) {
        this.cargoBookingCommandService = cargoBookingCommandService;
        this.cargoBookingQueryService = cargoBookingQueryService;
        this.cargoAssembler = cargoAssembler;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("bookings", cargoBookingQueryService.findAll().stream()
                .map(cargoAssembler::toResponse)
                .toList());
        return "booking/index";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute(BOOKING_ATTRIBUTE)) {
            model.addAttribute(BOOKING_ATTRIBUTE, new BookCargoRequest());
        }
        model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
        return NEW_VIEW;
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute(BOOKING_ATTRIBUTE) BookCargoRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
            return NEW_VIEW;
        }

        try {
            BookingId bookingId = cargoBookingCommandService.bookCargo(toCommand(request));
            return "redirect:/bookings/" + bookingId;
        } catch (IllegalArgumentException exception) {
            if (!"SHIPPER_NOT_FOUND".equals(exception.getMessage())) {
                throw exception;
            }
            bindingResult.rejectValue("shipperId", "notFound", "指定された荷主が見つかりません。");
            model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
            return NEW_VIEW;
        }
    }

    @GetMapping("/{bookingId}")
    public String show(@PathVariable String bookingId, Model model) {
        model.addAttribute(BOOKING_ATTRIBUTE, cargoBookingQueryService.findByBookingId(bookingId)
                .map(cargoAssembler::toResponse)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
        return "booking/show";
    }

    private BookCargoCommand toCommand(BookCargoRequest request) {
        return new BookCargoCommand(
                request.getShipperId(),
                request.getCargoType(),
                request.getWeight(),
                request.getOriginUnlocode(),
                request.getDestinationUnlocode(),
                request.getArrivalDeadline()
        );
    }
}
