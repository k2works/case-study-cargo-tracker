package com.example.cargotracker.bookingms.domain.ports;

import com.example.cargotracker.bookingms.domain.model.aggregates.Shipper;

import java.util.List;
import java.util.Optional;

public interface ShipperRepository {
    Shipper save(Shipper shipper);
    Optional<Shipper> findByEmail(String email);
    List<Shipper> findAll();
}
