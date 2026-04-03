package com.example.cargotracker.shared.infrastructure.web;

import com.example.cargotracker.billing.application.internal.queryservices.InvoiceQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.FindBookingQueryService;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.handling.application.internal.queryservices.FindHandlingEventsQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final FindBookingQueryService bookingQueryService;
    private final FindHandlingEventsQueryService handlingQueryService;
    private final InvoiceQueryService invoiceQueryService;

    public DashboardQueryService(FindBookingQueryService bookingQueryService,
                                  FindHandlingEventsQueryService handlingQueryService,
                                  InvoiceQueryService invoiceQueryService) {
        this.bookingQueryService = bookingQueryService;
        this.handlingQueryService = handlingQueryService;
        this.invoiceQueryService = invoiceQueryService;
    }

    public DashboardSummary getSummary() {
        var bookings = bookingQueryService.findAll();
        long total = bookings.size();
        long confirmed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long provisional = bookings.stream().filter(b -> b.getStatus() == BookingStatus.PROVISIONAL).count();

        // 支払い待ち（期限内・期限超過どちらも未払いとしてカウント）
        long pendingInvoices = invoiceQueryService.findAll().stream()
                .filter(inv -> "支払い待ち".equals(inv.paymentStatus())
                        || "支払い期限超過".equals(inv.paymentStatus()))
                .count();

        List<DashboardSummary.HandlingEventRow> recentEvents = handlingQueryService.findAll(10).stream()
                .map(e -> new DashboardSummary.HandlingEventRow(
                        e.getId().value().toString().substring(0, 8).toUpperCase(),
                        e.getBookingId().toString().substring(0, 8).toUpperCase(),
                        e.getEventType().name(),
                        e.getEventType().getDisplayName(),
                        e.getLocationCode(),
                        e.getCompletionTime()
                ))
                .toList();

        return new DashboardSummary(total, confirmed, provisional, pendingInvoices, recentEvents);
    }
}
