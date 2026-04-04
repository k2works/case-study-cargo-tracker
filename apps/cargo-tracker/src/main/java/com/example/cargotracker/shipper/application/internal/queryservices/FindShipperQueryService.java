package com.example.cargotracker.shipper.application.internal.queryservices;

import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class FindShipperQueryService {

    private final ShipperRepository shipperRepository;

    public FindShipperQueryService(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    public Optional<Shipper> findById(ShipperId id) {
        return shipperRepository.findById(id);
    }

    public List<Shipper> findAll() {
        return shipperRepository.findAll();
    }

    public Optional<Shipper> findByEmail(Email email) {
        return shipperRepository.findByEmail(email);
    }
}
