package com.example.cargotracker.shipper.application.internal.queryservices;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FindShipperQueryService {

    private final ShipperRepository shipperRepository;

    public FindShipperQueryService(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    public List<Shipper> findAll() {
        return shipperRepository.findAll();
    }

    public Shipper execute(ShipperId id) {
        return shipperRepository.findById(id)
                .orElseThrow(() -> new ShipperQueryNotFoundException(id.toString()));
    }
}
