package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.booking.application.internal.commandservices.AssignToRoutingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CancelBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.RouteCargoCommand;
import com.example.cargotracker.booking.application.internal.queryservices.CargoBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.exceptions.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.interfaces.rest.dto.BookCargoRequest;
import com.example.cargotracker.booking.interfaces.rest.transform.CargoAssembler;
import com.example.cargotracker.estimation.application.internal.commandservices.EstimateCommandService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.domain.model.Voyage;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
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
    private final EstimateCommandService estimateCommandService;
    private final VoyageQueryService voyageQueryService;

    public BookingThymeleafController(
            CargoBookingCommandService cargoBookingCommandService,
            CargoBookingQueryService cargoBookingQueryService,
            CargoAssembler cargoAssembler,
            FindShipperQueryService findShipperQueryService,
            ShipperAssembler shipperAssembler,
            EstimateCommandService estimateCommandService,
            VoyageQueryService voyageQueryService
    ) {
        this.cargoBookingCommandService = cargoBookingCommandService;
        this.cargoBookingQueryService = cargoBookingQueryService;
        this.cargoAssembler = cargoAssembler;
        this.findShipperQueryService = findShipperQueryService;
        this.shipperAssembler = shipperAssembler;
        this.estimateCommandService = estimateCommandService;
        this.voyageQueryService = voyageQueryService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("bookings", cargoBookingQueryService.findAll().stream()
                .map(cargoAssembler::toResponse)
                .toList());
        return "booking/index";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) String estimateId,
            @RequestParam(required = false) String originUnlocode,
            @RequestParam(required = false) String destinationUnlocode,
            @RequestParam(required = false) String arrivalDeadline,
            @RequestParam(required = false) String cargoType,
            @RequestParam(required = false) BigDecimal weightKg,
            Model model
    ) {
        if (!model.containsAttribute(BOOKING_ATTRIBUTE)) {
            BookCargoRequest request = new BookCargoRequest();
            if (originUnlocode != null) request.setOriginUnlocode(originUnlocode);
            if (destinationUnlocode != null) request.setDestinationUnlocode(destinationUnlocode);
            if (arrivalDeadline != null) request.setArrivalDeadline(LocalDate.parse(arrivalDeadline));
            if (cargoType != null) request.setCargoType(cargoType);
            if (weightKg != null) request.setWeight(weightKg);
            model.addAttribute(BOOKING_ATTRIBUTE, request);
        }
        if (estimateId != null) {
            model.addAttribute("estimateId", estimateId);
        }
        model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
        addShippersToModel(model);
        return NEW_VIEW;
    }

    @PostMapping
    public String create(
            @RequestParam(required = false) String estimateId,
            @Valid @ModelAttribute(BOOKING_ATTRIBUTE) BookCargoRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            if (estimateId != null) {
                model.addAttribute("estimateId", estimateId);
            }
            model.addAttribute(CARGO_TYPES_ATTRIBUTE, CargoType.values());
            addShippersToModel(model);
            return NEW_VIEW;
        }

        try {
            BookingId bookingId = cargoBookingCommandService.bookCargo(toCommand(request));
            if (estimateId != null) {
                estimateCommandService.markAsBooked(estimateId);
            }
            redirectAttributes.addFlashAttribute("successMessage", "予約を登録しました。（予約番号: " + bookingId + "）");
            return "redirect:/bookings/" + bookingId;
        } catch (ShipperNotFoundException exception) {
            bindingResult.rejectValue("shipperId", "notFound", "指定された荷主が見つかりません。");
            if (estimateId != null) {
                model.addAttribute("estimateId", estimateId);
            }
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
        return executeBookingCommand(bookingId, redirectAttributes, "予約を確定しました。",
                () -> cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(bookingId)));
    }

    @PostMapping("/{bookingId}/assign-to-routing")
    public String assignToRouting(@PathVariable String bookingId, RedirectAttributes redirectAttributes) {
        return executeBookingCommand(bookingId, redirectAttributes, "経路設計者に引き渡しました。",
                () -> cargoBookingCommandService.assignToRouting(new AssignToRoutingCommand(bookingId)));
    }

    @GetMapping("/{bookingId}/route")
    public String routeForm(
            @PathVariable String bookingId,
            @RequestParam(required = false) String originUnlocode,
            @RequestParam(required = false) String destinationUnlocode,
            @RequestParam(required = false) String arrivalDeadline,
            Model model
    ) {
        var booking = cargoBookingQueryService.findByBookingId(bookingId)
                .map(cargoAssembler::toResponse)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        String searchOrigin = originUnlocode != null ? originUnlocode : booking.originUnlocode();
        String searchDestination = destinationUnlocode != null ? destinationUnlocode : booking.destinationUnlocode();
        LocalDate searchDeadline = arrivalDeadline != null
                ? LocalDate.parse(arrivalDeadline)
                : booking.arrivalDeadline();

        var voyages = voyageQueryService.searchVoyages(searchOrigin, searchDestination, null, searchDeadline)
                .stream()
                .sorted(Comparator.comparingLong(Voyage::getDurationDays))
                .toList();

        model.addAttribute(BOOKING_ATTRIBUTE, booking);
        model.addAttribute("voyages", voyages);
        model.addAttribute("searchOrigin", searchOrigin);
        model.addAttribute("searchDestination", searchDestination);
        model.addAttribute("searchDeadline", searchDeadline);
        return "booking/route";
    }

    @GetMapping("/{bookingId}/route/detail")
    public String routeDetail(
            @PathVariable String bookingId,
            @RequestParam String voyageNumber,
            Model model
    ) {
        voyageQueryService.findByVoyageNumber(voyageNumber)
                .ifPresent(v -> model.addAttribute("selectedVoyage", v));
        return "booking/route :: routeDetail";
    }

    @PostMapping("/{bookingId}/route")
    public String assignRoute(
            @PathVariable String bookingId,
            @RequestParam String voyageNumber,
            RedirectAttributes redirectAttributes
    ) {
        try {
            cargoBookingCommandService.assignItinerary(new RouteCargoCommand(bookingId, voyageNumber));
            redirectAttributes.addFlashAttribute("successMessage", "経路を割り当てました。");
        } catch (BookingNotFoundException e) {
            throw new ResponseStatusException(NOT_FOUND);
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bookings/" + bookingId;
    }

    @PostMapping("/{bookingId}/cancel")
    public String cancel(@PathVariable String bookingId, RedirectAttributes redirectAttributes) {
        return executeBookingCommand(bookingId, redirectAttributes, "予約をキャンセルしました。",
                () -> cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId)));
    }

    private String executeBookingCommand(
            String bookingId,
            RedirectAttributes redirectAttributes,
            String successMessage,
            Runnable command
    ) {
        try {
            command.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
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
