package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindBookingQueryService {

    private final BookingRepository bookingRepository;

    public FindBookingQueryService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public Booking execute(BookingId id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id.value().toString()));
    }
}
