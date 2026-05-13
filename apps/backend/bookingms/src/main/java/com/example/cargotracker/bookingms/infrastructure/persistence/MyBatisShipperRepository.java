package com.example.cargotracker.bookingms.infrastructure.persistence;

import com.example.cargotracker.bookingms.domain.model.aggregates.Shipper;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.bookingms.domain.ports.ShipperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisShipperRepository implements ShipperRepository {

    private final ShipperMapper mapper;

    public MyBatisShipperRepository(ShipperMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Shipper save(Shipper shipper) {
        ShipperRecord shipperRow = toRecord(shipper);
        mapper.insert(shipperRow);
        return new Shipper(new Shipper.PersistedState(
                shipperRow.getId(),
                shipper.getShipperCode(),
                shipper.getShipperType(),
                shipper.getName(),
                shipper.getEmail(),
                shipper.getPhone(),
                shipper.getContractNumber(),
                shipper.getDiscountRate()));
    }

    @Override
    public Optional<Shipper> findByEmail(String email) {
        ShipperRecord shipperRow = mapper.findByEmail(email);
        return Optional.ofNullable(shipperRow).map(this::toDomain);
    }

    @Override
    public List<Shipper> findAll() {
        return mapper.findAll().stream().map(this::toDomain).toList();
    }

    private ShipperRecord toRecord(Shipper shipper) {
        ShipperRecord r = new ShipperRecord();
        r.setShipperCode(shipper.getShipperCode());
        r.setShipperType(shipper.getShipperType().name());
        r.setName(shipper.getName());
        r.setEmail(shipper.getEmail());
        r.setPhone(shipper.getPhone());
        r.setContractNumber(shipper.getContractNumber());
        r.setDiscountRate(shipper.getDiscountRate());
        return r;
    }

    private Shipper toDomain(ShipperRecord r) {
        return new Shipper(new Shipper.PersistedState(
                r.getId(),
                r.getShipperCode(),
                ShipperType.valueOf(r.getShipperType()),
                r.getName(),
                r.getEmail(),
                r.getPhone(),
                r.getContractNumber(),
                r.getDiscountRate()));
    }
}
