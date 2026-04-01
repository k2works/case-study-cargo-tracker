package com.example.cargotracker.shipper.infrastructure.persistence;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.*;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class ShipperRepositoryImpl implements ShipperRepository {

    private final ShipperMapper shipperMapper;

    public ShipperRepositoryImpl(ShipperMapper shipperMapper) {
        this.shipperMapper = shipperMapper;
    }

    @Override
    public void save(Shipper shipper) {
        CorporateContractInfo corp = shipper.getCorporateContractInfo();
        ShipperRecord record = new ShipperRecord(
                shipper.getId().value(),
                shipper.getName().value(),
                shipper.getContactInfo().email(),
                shipper.getContactInfo().phone(),
                null,
                shipper.getCategory().name(),
                corp != null ? corp.contractNumber() : null,
                corp != null ? corp.discountRate() : null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        shipperMapper.insert(record);
    }

    @Override
    public Optional<Shipper> findById(ShipperId id) {
        return shipperMapper.findById(id.value())
                .map(this::toShipper);
    }

    @Override
    public Optional<Shipper> findByEmail(String email) {
        return shipperMapper.findByEmail(email)
                .map(this::toShipper);
    }

    private Shipper toShipper(ShipperRecord record) {
        ShipperId id = new ShipperId(record.id());
        ShipperName name = new ShipperName(record.name());
        ContactInfo contactInfo = new ContactInfo(record.email(), record.phone());
        CustomerCategory category = CustomerCategory.valueOf(record.category());

        if (category == CustomerCategory.CORPORATE && record.contractNumber() != null) {
            CorporateContractInfo corp = new CorporateContractInfo(
                    record.contractNumber(), record.discountRate());
            return Shipper.registerCorporate(id, name, contactInfo, corp);
        }
        return Shipper.registerIndividual(id, name, contactInfo);
    }
}
