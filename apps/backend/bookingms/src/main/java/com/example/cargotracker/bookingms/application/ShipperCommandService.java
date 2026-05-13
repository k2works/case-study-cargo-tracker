package com.example.cargotracker.bookingms.application;

import com.example.cargotracker.bookingms.domain.model.aggregates.Shipper;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ShipperCommandService {

    public record RegisterShipperCommand(String name, String email, String phone,
                                         String shipperType,
                                         String contractNumber, BigDecimal discountRate) {}

    private final ShipperRepository repository;

    public ShipperCommandService(ShipperRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Shipper register(RegisterShipperCommand command) {
        repository.findByEmail(command.email()).ifPresent(existing -> {
            throw new IllegalStateException("メールアドレスが既に登録されています: " + command.email());
        });

        Shipper shipper;
        if ("CORPORATE".equalsIgnoreCase(command.shipperType())) {
            shipper = Shipper.createCorporate(command.name(), command.email(), command.phone(),
                    command.contractNumber(), command.discountRate());
        } else {
            shipper = Shipper.createIndividual(command.name(), command.email(), command.phone());
        }

        return repository.save(shipper);
    }
}
