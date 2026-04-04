package com.example.cargotracker.shipper.infrastructure.repositories;

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
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisShipperRepository implements ShipperRepository {

    private final ShipperMapper shipperMapper;

    public MyBatisShipperRepository(ShipperMapper shipperMapper) {
        this.shipperMapper = shipperMapper;
    }

    @Override
    public void save(Shipper shipper) {
        shipperMapper.insert(toRecord(shipper));
    }

    @Override
    public Optional<Shipper> findById(ShipperId id) {
        return Optional.ofNullable(shipperMapper.findById(id.toString())).map(this::toDomain);
    }

    @Override
    public Optional<Shipper> findByEmail(Email email) {
        return Optional.ofNullable(shipperMapper.findByEmail(email.getValue())).map(this::toDomain);
    }

    @Override
    public Optional<Shipper> findByCode(ShipperCode code) {
        return Optional.ofNullable(shipperMapper.findByCode(code.getValue())).map(this::toDomain);
    }

    @Override
    public List<Shipper> findAll() {
        return shipperMapper.findAll().stream().map(this::toDomain).toList();
    }

    private ShipperRecord toRecord(Shipper shipper) {
        ShipperRecord record = new ShipperRecord();
        record.setId(shipper.getId().toString());
        record.setShipperCode(shipper.getCode().getValue());
        record.setShipperType(shipper.getShipperType().name());
        record.setName(shipper.getName().getValue());
        record.setEmail(shipper.getEmail().getValue());
        record.setPhone(shipper.getPhone() == null ? null : shipper.getPhone().getValue());
        if (shipper instanceof CorporateShipper corporateShipper) {
            record.setContractNumber(corporateShipper.getContractNumber().getValue());
            record.setDiscountRate(corporateShipper.getDiscountRate().getValue());
        }
        return record;
    }

    private Shipper toDomain(ShipperRecord record) {
        ShipperType shipperType = ShipperType.valueOf(record.getShipperType());
        if (shipperType == ShipperType.CORPORATE) {
            return new CorporateShipper(
                    new ShipperId(UUID.fromString(record.getId())),
                    new ShipperCode(record.getShipperCode()),
                    new ShipperName(record.getName()),
                    new Email(record.getEmail()),
                    toPhone(record.getPhone()),
                    new ContractNumber(record.getContractNumber()),
                    new DiscountRate(record.getDiscountRate())
            );
        }
        return new Shipper(
                new ShipperId(UUID.fromString(record.getId())),
                new ShipperCode(record.getShipperCode()),
                new ShipperName(record.getName()),
                new Email(record.getEmail()),
                toPhone(record.getPhone()),
                shipperType
        );
    }

    private Phone toPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return new Phone(phone);
    }
}
