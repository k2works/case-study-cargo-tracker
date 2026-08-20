package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.ShipperRepository;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.Shipper;
import com.example.bookingms.domain.model.ShipperType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisShipperRepository implements ShipperRepository {

    private final ShipperMapper mapper;

    public MyBatisShipperRepository(ShipperMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Shipper> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email)).map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public Optional<Shipper> findById(Long id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(mapper.findById(id)).map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public Shipper save(Shipper shipper) {
        ShipperRecord row = new ShipperRecord();
        // 採番はシーケンスに任せる。テストでも本番と同じ経路を通す
        row.setShipperCode("SHP-%06d".formatted(mapper.nextShipperCodeNumber()));
        row.setShipperType(shipper.type().name());
        row.setName(shipper.name());
        row.setEmail(shipper.email());
        row.setAddress(shipper.address());
        row.setPhone(shipper.phone());
        row.setContractNumber(shipper.contractNumber().map(ContractNumber::value).orElse(null));
        row.setDiscountRate(shipper.discountRate().map(DiscountRate::rate).orElse(null));
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public List<Shipper> search(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.search(normalized).stream().map(MyBatisShipperRepository::toDomain).toList();
    }

    /** 契約番号が入っている行だけ契約を復元する。無い行は個人か、列が無かったころの行。 */
    private static CorporateContract contractOf(ShipperRecord row) {
        if (row.getContractNumber() == null) {
            return null;
        }
        return new CorporateContract(
                ContractNumber.of(row.getContractNumber()),
                row.getDiscountRate() == null ? null : DiscountRate.ofRate(row.getDiscountRate()));
    }

    private static Shipper toDomain(ShipperRecord row) {
        return Shipper.restore(
                row.getId(),
                row.getShipperCode(),
                ShipperType.valueOf(row.getShipperType()),
                row.getName(),
                row.getEmail(),
                row.getAddress(),
                row.getPhone(),
                // 復元では検査しない。列が無かったころの行が読めなくなる
                contractOf(row));
    }
}
