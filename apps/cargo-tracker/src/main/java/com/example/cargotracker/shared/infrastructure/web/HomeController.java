package com.example.cargotracker.shared.infrastructure.web;

import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentStatus;
import com.example.cargotracker.booking.application.internal.queryservices.CargoBookingQueryService;
import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CargoBookingQueryService cargoBookingQueryService;
    private final InvoiceQueryService invoiceQueryService;
    private final TrackingQueryService trackingQueryService;

    public HomeController(CargoBookingQueryService cargoBookingQueryService,
                          InvoiceQueryService invoiceQueryService,
                          TrackingQueryService trackingQueryService) {
        this.cargoBookingQueryService = cargoBookingQueryService;
        this.invoiceQueryService = invoiceQueryService;
        this.trackingQueryService = trackingQueryService;
    }

    @GetMapping("/")
    public String index(Model model) {
        var allCargos = cargoBookingQueryService.findAll();
        long totalBookings = allCargos.size();
        long inTransitCount = allCargos.stream()
                .filter(c -> c.getStatus() == BookingStatus.IN_TRANSIT)
                .count();
        long unassignedCount = allCargos.stream()
                .filter(c -> c.getStatus() == BookingStatus.PRELIMINARY)
                .count();
        long pendingInvoiceCount = invoiceQueryService.findAll().stream()
                .filter(i -> i.getPaymentStatus() == PaymentStatus.PENDING)
                .count();
        var latestHandlingEvents = trackingQueryService.findLatestHandlingEvents(10);

        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("inTransitCount", inTransitCount);
        model.addAttribute("unassignedCount", unassignedCount);
        model.addAttribute("pendingInvoiceCount", pendingInvoiceCount);
        model.addAttribute("latestHandlingEvents", latestHandlingEvents);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
