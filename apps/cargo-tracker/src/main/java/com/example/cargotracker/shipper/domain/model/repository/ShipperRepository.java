package com.example.cargotracker.shipper.domain.model.repository;

import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;

import java.util.List;
import java.util.Optional;

public interface ShipperRepository {

    void save(Shipper shipper);

    Optional<Shipper> findById(ShipperId id);

    Optional<Shipper> findByEmail(Email email);

    Optional<Shipper> findByCode(ShipperCode code);

    List<Shipper> findAll();
}
