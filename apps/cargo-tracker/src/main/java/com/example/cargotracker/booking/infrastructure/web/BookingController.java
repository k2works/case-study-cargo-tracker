package com.example.cargotracker.booking.infrastructure.web;

import com.example.cargotracker.booking.application.BookingNotFoundException;
import com.example.cargotracker.booking.application.query.FindBookingUseCase;
import com.example.cargotracker.booking.application.command.RegisterBookingUseCase;
import com.example.cargotracker.booking.application.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.Booking;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final RegisterBookingUseCase registerBookingUseCase;
    private final FindBookingUseCase findBookingUseCase;
    private final ShipperRepository shipperRepository;

    public BookingController(RegisterBookingUseCase registerBookingUseCase,
                              FindBookingUseCase findBookingUseCase,
                              ShipperRepository shipperRepository) {
        this.registerBookingUseCase = registerBookingUseCase;
        this.findBookingUseCase = findBookingUseCase;
        this.shipperRepository = shipperRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("bookings", List.of());
        return "booking/list";
    }

    @GetMapping("/new")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new BookingRegisterForm());
        model.addAttribute("cargoTypes", CargoType.values());
        return "booking/register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") BookingRegisterForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("cargoTypes", CargoType.values());
            return "booking/register";
        }

        try {
            BookingId bookingId = registerBookingUseCase.execute(form.toCommand());
            redirectAttributes.addFlashAttribute("successMessage",
                    "予約を登録しました（予約番号: " + bookingId + "）");
            return "redirect:/bookings/" + bookingId;
        } catch (ShipperNotFoundException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("cargoTypes", CargoType.values());
            return "booking/register";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String id, Model model) {
        BookingId bookingId;
        try {
            bookingId = new BookingId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new BookingNotFoundException(id);
        }
        Booking booking = findBookingUseCase.execute(bookingId);
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
            ShipperId id = new ShipperId(UUID.fromString(shipperId));
            shipperRepository.findById(id).ifPresentOrElse(
                    shipper -> model.addAttribute("shipperName", shipper.getName().value()),
                    () -> model.addAttribute("shipperName", "（荷主が見つかりません）")
            );
        } catch (Exception e) {
            model.addAttribute("shipperName", "（無効な荷主 ID です）");
        }
        return "booking/fragments/shipper-name";
    }
}
