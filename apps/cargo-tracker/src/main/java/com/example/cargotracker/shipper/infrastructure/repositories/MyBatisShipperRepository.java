package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shared.domain.model.ShipperId;
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
        return Optional.ofNullable(shipperMapper.findByEmail(email.value())).map(this::toDomain);
    }

    @Override
    public Optional<Shipper> findByCode(ShipperCode code) {
        return Optional.ofNullable(shipperMapper.findByCode(code.value())).map(this::toDomain);
    }

    @Override
    public List<Shipper> findAll() {
        return shipperMapper.findAll().stream().map(this::toDomain).toList();
    }

    private ShipperRecord toRecord(Shipper shipper) {
        ShipperRecord shipperRecord = new ShipperRecord();
        shipperRecord.setId(shipper.getId().toString());
        shipperRecord.setShipperCode(shipper.getCode().value());
        shipperRecord.setShipperType(shipper.getShipperType().name());
        shipperRecord.setName(shipper.getName().value());
        shipperRecord.setEmail(shipper.getEmail().value());
        shipperRecord.setPhone(shipper.getPhone() == null ? null : shipper.getPhone().value());
        shipperRecord.setAddress(shipper.getAddress() == null ? null : shipper.getAddress().value());
        if (shipper instanceof CorporateShipper corporateShipper) {
            shipperRecord.setContractNumber(corporateShipper.getContractNumber().value());
            shipperRecord.setDiscountRate(corporateShipper.getDiscountRate().value());
        }
        return shipperRecord;
    }

    private Shipper toDomain(ShipperRecord shipperRecord) {
        ShipperType shipperType = ShipperType.valueOf(shipperRecord.getShipperType());
        Address address = toAddress(shipperRecord.getAddress());
        if (shipperType == ShipperType.CORPORATE) {
            return new CorporateShipper(
                    new ShipperId(UUID.fromString(shipperRecord.getId())),
                    new ShipperCode(shipperRecord.getShipperCode()),
                    new ShipperName(shipperRecord.getName()),
                    new Email(shipperRecord.getEmail()),
                    toPhone(shipperRecord.getPhone()),
                    address,
                    new ContractNumber(shipperRecord.getContractNumber()),
                    new DiscountRate(shipperRecord.getDiscountRate())
            );
        }
        return new Shipper(
                new ShipperId(UUID.fromString(shipperRecord.getId())),
                new ShipperCode(shipperRecord.getShipperCode()),
                new ShipperName(shipperRecord.getName()),
                new Email(shipperRecord.getEmail()),
                toPhone(shipperRecord.getPhone()),
                address,
                shipperType
        );
    }

    private Phone toPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return new Phone(phone);
    }

    private Address toAddress(String address) {
        if (address == null) {
            return null;
        }
        return new Address(address);
    }
}
