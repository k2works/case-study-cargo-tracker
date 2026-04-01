package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContractInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterShipperCommandService {

    private final ShipperRepository shipperRepository;

    public RegisterShipperCommandService(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    public ShipperId execute(RegisterShipperCommand command) {
        shipperRepository.findByEmail(command.email()).ifPresent(existing -> {
            throw new DuplicateShipperException(existing.getId());
        });

        ShipperId id = ShipperId.generate();
        ShipperName name = new ShipperName(command.name());
        ContactInfo contactInfo = new ContactInfo(command.email(), command.phone());

        Shipper shipper;
        if (command.category() == CustomerCategory.CORPORATE) {
            CorporateContractInfo corp = new CorporateContractInfo(
                    command.contractNumber(), command.discountRate());
            shipper = Shipper.registerCorporate(id, name, contactInfo, corp);
        } else {
            shipper = Shipper.registerIndividual(id, name, contactInfo);
        }

        shipperRepository.save(shipper);
        return id;
    }
}
