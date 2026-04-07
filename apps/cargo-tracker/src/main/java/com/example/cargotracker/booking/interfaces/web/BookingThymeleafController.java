package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.AssignToRoutingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CancelBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.queryservices.CargoBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.exceptions.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.transform.CargoAssembler;
import com.example.cargotracker.shipper.application.internal.queryservices.FindShipperQueryService;
import com.example.cargotracker.shipper.interfaces.rest.transform.ShipperAssembler;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/bookings")
public class BookingThymeleafController {

    private static final String BOOKING_ATTRIBUTE = "booking";
    private static final String CARGO_TYPES_ATTRIBUTE = "cargoTypes";
    private static final String SHIPPERS_ATTRIBUTE = "shippers";
    private static final String NEW_VIEW = "booking/new";

    private final CargoBookingCommandService cargoBookingCommandService;
    private final CargoBookingQueryService cargoBookingQueryService;
    private final CargoAssembler cargoAssembler;
    private final FindShipperQueryService findShipperQueryService;
    private final ShipperAssembler shipperAssembler;

    public BookingThymeleafController(
            CargoBookingCommandService cargoBookingCommandService,
            CargoBookingQueryService cargoBookingQueryService,
            CargoAssembler cargoAssembler,
            FindShipperQueryService findShipperQueryService,
            ShipperAssembler shipperAssembler
    ) {
        this.cargoBookingCommandService = cargoBookingCommandService;
        this.cargoBookingQueryService = cargoBookingQueryService;
        this.cargoAssembler = cargoAssembler;
        this.findShipperQueryService = findShipperQueryService;
        this.shipperAssembler = shipperAssembler;
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
        addShippersToModel(model);
        return NEW_VIEW;
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute(BOOKING_ATTRIBUTE) BookCargoRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
            addShippersToModel(model);
            return NEW_VIEW;
        }

        try {
            BookingId bookingId = cargoBookingCommandService.bookCargo(toCommand(request));
            redirectAttributes.addFlashAttribute("successMessage", "予約を登録しました。（予約番号: " + bookingId + "）");
            return "redirect:/bookings/" + bookingId;
        } catch (ShipperNotFoundException exception) {
            bindingResult.rejectValue("shipperId", "notFound", "指定された荷主が見つかりません。");
            model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
            addShippersToModel(model);
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

    @PostMapping("/{bookingId}/confirm")
    public String confirm(@PathVariable String bookingId, RedirectAttributes redirectAttributes) {
        try {
            cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(bookingId));
            redirectAttributes.addFlashAttribute("successMessage", "予約を確定しました。");
        } catch (BookingNotFoundException e) {
            throw new ResponseStatusException(NOT_FOUND);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bookings/" + bookingId;
    }

    @PostMapping("/{bookingId}/assign-to-routing")
    public String assignToRouting(@PathVariable String bookingId, RedirectAttributes redirectAttributes) {
        try {
            cargoBookingCommandService.assignToRouting(new AssignToRoutingCommand(bookingId));
            redirectAttributes.addFlashAttribute("successMessage", "経路設計者に引き渡しました。");
        } catch (BookingNotFoundException e) {
            throw new ResponseStatusException(NOT_FOUND);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bookings/" + bookingId;
    }

    @PostMapping("/{bookingId}/cancel")
    public String cancel(@PathVariable String bookingId, RedirectAttributes redirectAttributes) {
        try {
            cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId));
            redirectAttributes.addFlashAttribute("successMessage", "予約をキャンセルしました。");
        } catch (BookingNotFoundException e) {
            throw new ResponseStatusException(NOT_FOUND);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bookings/" + bookingId;
    }

    private void addShippersToModel(Model model) {
        model.addAttribute(SHIPPERS_ATTRIBUTE, findShipperQueryService.findAll().stream()
                .map(shipperAssembler::toResponse)
                .toList());
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
}
