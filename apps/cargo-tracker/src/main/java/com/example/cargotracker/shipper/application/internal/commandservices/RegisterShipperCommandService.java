package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RegisterShipperCommandService {

    private static final String AUTO_CODE_PREFIX = "SHP-";
    private static final int AUTO_CODE_LENGTH = 8;

    private final ShipperRepository shipperRepository;

    public RegisterShipperCommandService(ShipperRepository shipperRepository) {
        this.shipperRepository = shipperRepository;
    }

    public ShipperId registerShipper(RegisterShipperCommand command) {
        Email email = new Email(command.email());
        if (shipperRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("EMAIL_ALREADY_REGISTERED");
        }

        Shipper shipper = createShipper(command, email);
        shipperRepository.save(shipper);
        return shipper.getId();
    }

    private Shipper createShipper(RegisterShipperCommand command, Email email) {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        ShipperCode shipperCode = new ShipperCode(resolveCode(command.code()));
        ShipperName shipperName = new ShipperName(command.name());
        Phone phone = command.phone() == null ? null : new Phone(command.phone());

        if (command.shipperType() == ShipperType.CORPORATE) {
            return new CorporateShipper(
                    shipperId,
                    shipperCode,
                    shipperName,
                    email,
                    phone,
                    new ContractNumber(command.contractNumber()),
                    new DiscountRate(command.discountRate())
            );
        }

        return new Shipper(
                shipperId,
                shipperCode,
                shipperName,
                email,
                phone,
                command.shipperType()
        );
    }

    private String resolveCode(String code) {
        if (code == null || code.isBlank()) {
            return AUTO_CODE_PREFIX + UUID.randomUUID().toString().substring(0, AUTO_CODE_LENGTH);
        }
        return code;
    }
}
