package com.example.cargotracker.booking.domain.model.repository;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.model.ShipperId;

import java.util.List;
import java.util.Optional;

public interface CargoRepository {

    void save(Cargo cargo);

    void updateStatus(Cargo cargo);

    void updateTrackingNumber(Cargo cargo);

    Optional<Cargo> findByBookingId(BookingId bookingId);

    List<Cargo> findAll();

    List<Cargo> findByShipperId(ShipperId shipperId);
}
