package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignRouteCommandService {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AssignRouteCommandService(BookingRepository bookingRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    public void execute(AssignRouteCommand command) {
        BookingId bookingId = new BookingId(command.bookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("予約が見つかりません: " + command.bookingId()));

        AssignedRoute route = new AssignedRoute(
                command.voyageNumber(),
                command.routePath(),
                command.estimatedArrival()
        );
        booking.assignRoute(route);
        bookingRepository.save(booking);

        booking.getDomainEvents().forEach(eventPublisher::publishEvent);
    }
}
