package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConfirmBookingCommandService {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ConfirmBookingCommandService(BookingRepository bookingRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    public void execute(ConfirmBookingCommand command) {
        BookingId bookingId = new BookingId(command.bookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("予約が見つかりません: " + command.bookingId()));

        booking.confirm();
        bookingRepository.save(booking);

        booking.getDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
