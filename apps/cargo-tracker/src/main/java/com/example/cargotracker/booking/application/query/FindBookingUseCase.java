package com.example.cargotracker.booking.application.query;

import com.example.cargotracker.booking.application.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.Booking;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindBookingUseCase {

    private final BookingRepository bookingRepository;

    public FindBookingUseCase(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public Booking execute(BookingId id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id.value().toString()));
    }
}
