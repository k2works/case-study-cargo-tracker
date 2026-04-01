package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CorporateContractInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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
        ShipperRecord row = new ShipperRecord(
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
        shipperMapper.insert(row);
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

    @Override
    public List<Shipper> findAll() {
        return shipperMapper.findAll().stream()
                .map(this::toShipper)
                .toList();
    }

    private Shipper toShipper(ShipperRecord row) {
        ShipperId id = new ShipperId(row.id());
        ShipperName name = new ShipperName(row.name());
        ContactInfo contactInfo = new ContactInfo(row.email(), row.phone());
        CustomerCategory category = CustomerCategory.valueOf(row.category());

        if (category == CustomerCategory.CORPORATE && row.contractNumber() != null) {
            CorporateContractInfo corp = new CorporateContractInfo(
                    row.contractNumber(), row.discountRate());
            return Shipper.registerCorporate(id, name, contactInfo, corp);
        }
        return Shipper.registerIndividual(id, name, contactInfo);
    }
}
