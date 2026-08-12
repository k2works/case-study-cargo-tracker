package com.example.cargotracker.shipper.infrastructure.repositories;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.entities.CorporateContract;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperContact;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperType;
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
        // 個人荷主は契約を持たない。**割引率も NULL にしない**
        //（列は NOT NULL DEFAULT 0。個人の 0 は「割引なし」ではなく「概念が無い」であり、
        // 画面は種別で出し分ける）
        r.setContractNumber(s.hasContract() ? s.contract().contractNumber().value() : null);
        r.setDiscountRate(s.hasContract()
                ? s.contract().discountRate().value() : java.math.BigDecimal.ZERO);
        r.setVersion(s.version());
        return r;
    }

    private static Shipper toDomain(ShipperRecord r) {
        return new Shipper(
                new ShipperId(r.getId()),
                new ShipperCode(r.getShipperCode()),
                ShipperType.valueOf(r.getShipperType()),
                new ShipperName(r.getName()),
                new ShipperContact(
                        new Email(r.getEmail()),
                        new Phone(r.getPhone()),
                        new Address(
                                r.getAddressCountry(), r.getAddressPostalCode(),
                                r.getAddressRegion(), r.getAddressCity(), r.getAddressStreet())),
                // **契約番号の有無で判断する。** 割引率は列の DEFAULT により
                // 個人荷主でも 0 が入っており、有無の判断には使えない
                r.getContractNumber() == null ? null : new CorporateContract(
                        new ContractNumber(r.getContractNumber()),
                        new DiscountRate(r.getDiscountRate())),
                r.getVersion());
    }
}
