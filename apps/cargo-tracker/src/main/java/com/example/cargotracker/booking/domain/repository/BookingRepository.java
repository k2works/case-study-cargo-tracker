package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;

import java.util.Optional;

public interface BookingRepository {

    void save(Booking booking);

    Optional<Booking> findById(BookingId id);
}
