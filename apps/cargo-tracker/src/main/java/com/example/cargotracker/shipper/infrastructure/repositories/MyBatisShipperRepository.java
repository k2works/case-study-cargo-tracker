package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.Shipper;
import com.example.cargotracker.shipper.domain.model.ShipperCode;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import com.example.cargotracker.shipper.domain.model.ShipperType;
import com.example.cargotracker.shipper.domain.repository.ShipperRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link ShipperRepository} の MyBatis 実装（出力アダプタ）。 */
@Repository
public class MyBatisShipperRepository implements ShipperRepository {

    private final ShipperMapper mapper;

    public MyBatisShipperRepository(ShipperMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Shipper shipper) {
        mapper.insert(toRecord(shipper));
    }

    @Override
    public Optional<Shipper> findById(ShipperId id) {
        return Optional.ofNullable(mapper.findById(id.value())).map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public Optional<Shipper> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email)).map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public Optional<Shipper> findByShipperCode(String shipperCode) {
        return Optional.ofNullable(mapper.findByShipperCode(shipperCode))
                .map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public List<Shipper> findAll() {
        return mapper.findAll().stream().map(MyBatisShipperRepository::toDomain).toList();
    }

    @Override
    public long nextSequence() {
        return mapper.nextSequence();
    }

    @Override
    public boolean update(Shipper shipper) {
        return mapper.update(toRecord(shipper)) == 1;
    }

    private static ShipperRecord toRecord(Shipper s) {
        ShipperRecord r = new ShipperRecord();
        r.setId(s.id().value());
        r.setShipperCode(s.shipperCode().value());
        r.setShipperType(s.shipperType().name());
        r.setName(s.name().value());
        r.setEmail(s.email().value());
        r.setPhone(s.phone().value());
        r.setAddressCountry(s.address().country());
        r.setAddressPostalCode(s.address().postalCode());
        r.setAddressRegion(s.address().region());
        r.setAddressCity(s.address().city());
        r.setAddressStreet(s.address().street());
        r.setVersion(s.version());
        return r;
    }

    private static Shipper toDomain(ShipperRecord r) {
        return new Shipper(
                new ShipperId(r.getId()),
                new ShipperCode(r.getShipperCode()),
                ShipperType.valueOf(r.getShipperType()),
                new ShipperName(r.getName()),
                new com.example.cargotracker.shipper.domain.model.ShipperContact(
                        new Email(r.getEmail()),
                        new Phone(r.getPhone()),
                        new Address(
                                r.getAddressCountry(), r.getAddressPostalCode(),
                                r.getAddressRegion(), r.getAddressCity(), r.getAddressStreet())),
                r.getVersion());
    }
}
