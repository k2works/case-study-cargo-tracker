package com.example.cargotracker.booking.domain.model.repository;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;

import java.util.List;

public interface LegRepository {

    void saveAll(BookingId bookingId, List<Leg> legs);

    List<Leg> findByBookingId(BookingId bookingId);
}
