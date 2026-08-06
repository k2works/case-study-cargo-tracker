package com.example.cargotracker.billing.infrastructure.services;

import com.example.cargotracker.billing.application.internal.outboundservices.acl.BookingSettlementPort;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.SettleBookingCommand;
import org.springframework.stereotype.Component;

@Component
public class BookingSettlementAdapter implements BookingSettlementPort {

    private final CargoBookingCommandService cargoBookingCommandService;

    public BookingSettlementAdapter(CargoBookingCommandService cargoBookingCommandService) {
        this.cargoBookingCommandService = cargoBookingCommandService;
    }

    @Override
    public void settleBooking(String bookingId) {
        cargoBookingCommandService.settleBooking(new SettleBookingCommand(bookingId));
    }
}
