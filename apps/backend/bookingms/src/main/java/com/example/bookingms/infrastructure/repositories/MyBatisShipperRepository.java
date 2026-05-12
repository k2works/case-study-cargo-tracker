package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.model.valueobjects.ShipperType;
import com.example.bookingms.domain.ports.ShipperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis を使用した荷主リポジトリ実装
 */
@Repository
public class MyBatisShipperRepository implements ShipperRepository {

    private final ShipperMapper shipperMapper;

    public MyBatisShipperRepository(ShipperMapper shipperMapper) {
        this.shipperMapper = shipperMapper;
    }

    @Override
    public Shipper save(Shipper shipper) {
        ShipperRecord shipperRecord = toRecord(shipper);
        shipperRecord.setShipperCode(generateShipperCode());
        shipperMapper.insertShipper(shipperRecord);
        return new Shipper(new Shipper.PersistedState(
                shipperRecord.getId(),
                shipperRecord.getShipperCode(),
                ShipperType.valueOf(shipperRecord.getShipperType()),
                shipperRecord.getName(),
                shipperRecord.getEmail(),
                shipperRecord.getPhone(),
                shipperRecord.getContractNumber(),
                shipperRecord.getDiscountRate()
        ));
    }

    @Override
    public Optional<Shipper> findByEmail(String email) {
        return shipperMapper.findByEmail(email).map(this::toDomain);
    }

    @Override
    public List<Shipper> findAll() {
        return shipperMapper.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private ShipperRecord toRecord(Shipper shipper) {
        ShipperRecord shipperRecord = new ShipperRecord();
        shipperRecord.setShipperType(shipper.getShipperType().name());
        shipperRecord.setName(shipper.getName());
        shipperRecord.setEmail(shipper.getEmail());
        shipperRecord.setPhone(shipper.getPhone());
        shipperRecord.setContractNumber(shipper.getContractNumber());
        shipperRecord.setDiscountRate(shipper.getDiscountRate());
        return shipperRecord;
    }

    private Shipper toDomain(ShipperRecord shipperRecord) {
        return new Shipper(new Shipper.PersistedState(
                shipperRecord.getId(),
                shipperRecord.getShipperCode(),
                ShipperType.valueOf(shipperRecord.getShipperType()),
                shipperRecord.getName(),
                shipperRecord.getEmail(),
                shipperRecord.getPhone(),
                shipperRecord.getContractNumber(),
                shipperRecord.getDiscountRate()
        ));
    }

    private String generateShipperCode() {
        return "SHP-" + String.format("%06d", System.currentTimeMillis() % 1000000);
    }
}
