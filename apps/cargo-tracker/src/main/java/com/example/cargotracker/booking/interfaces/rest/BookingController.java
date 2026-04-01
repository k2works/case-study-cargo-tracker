package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.internal.commandservices.RegisterBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ShipperNotFoundException;
import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import com.example.cargotracker.booking.application.internal.queryservices.BookingNotFoundException;
import com.example.cargotracker.booking.application.internal.queryservices.FindBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.interfaces.rest.dto.BookingRegisterForm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private static final String VIEW_REGISTER = "booking/register";
    private static final String ATTR_CARGO_TYPES = "cargoTypes";
    private static final String ATTR_SHIPPERS = "shippers";
    private static final String ATTR_SHIPPER_NAME = "shipperName";

    private final RegisterBookingCommandService registerBookingCommandService;
    private final FindBookingQueryService findBookingQueryService;
    private final ShipperExistencePort shipperExistencePort;

    public BookingController(RegisterBookingCommandService registerBookingCommandService,
                              FindBookingQueryService findBookingQueryService,
                              ShipperExistencePort shipperExistencePort) {
        this.registerBookingCommandService = registerBookingCommandService;
        this.findBookingQueryService = findBookingQueryService;
        this.shipperExistencePort = shipperExistencePort;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("bookings", findBookingQueryService.findAll());
        return "booking/list";
    }

    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new BookingRegisterForm());
        populateRegisterFormOptions(model);
        return VIEW_REGISTER;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") BookingRegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            populateRegisterFormOptions(model);
            return VIEW_REGISTER;
        }

        try {
            BookingId bookingId = registerBookingCommandService.execute(form.toCommand());
            redirectAttributes.addFlashAttribute("successMessage",
                    "予約を登録しました（予約番号: " + bookingId + "）");
            return "redirect:/bookings/" + bookingId;
        } catch (ShipperNotFoundException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateRegisterFormOptions(model);
            return VIEW_REGISTER;
        }
    }

    private void populateRegisterFormOptions(Model model) {
        model.addAttribute(ATTR_CARGO_TYPES, CargoType.values());
        model.addAttribute(ATTR_SHIPPERS, shipperExistencePort.findAll());
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String id, Model model) {
        BookingId bookingId;
        try {
            bookingId = new BookingId(UUID.fromString(id));
        } catch (IllegalArgumentException _) {
            throw new BookingNotFoundException(id);
        }
        Booking booking = findBookingQueryService.execute(bookingId);
        model.addAttribute("booking", booking);
        return "booking/detail";
    }

    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleBookingNotFound(BookingNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/404";
    }

    @PostMapping("/lookup-shipper")
    public String lookupShipper(@RequestParam("shipperId") String shipperId, Model model) {
        try {
            UUID id = UUID.fromString(shipperId);
            shipperExistencePort.findNameById(id).ifPresentOrElse(
                    name -> model.addAttribute(ATTR_SHIPPER_NAME, name),
                    () -> model.addAttribute(ATTR_SHIPPER_NAME, "（荷主が見つかりません）")
            );
        } catch (Exception _) {
            model.addAttribute(ATTR_SHIPPER_NAME, "（無効な荷主 ID です）");
        }
        return "booking/fragments/shipper-name";
    }
}
