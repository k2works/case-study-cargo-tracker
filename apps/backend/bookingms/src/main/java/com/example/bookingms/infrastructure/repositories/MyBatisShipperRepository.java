package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.repository.ShipperRepository;
import com.example.bookingms.domain.model.valueobjects.ContractNumber;
import com.example.bookingms.domain.model.valueobjects.EmailAddress;
import com.example.bookingms.domain.model.valueobjects.CorporateContract;
import com.example.bookingms.domain.model.valueobjects.DiscountRate;
import com.example.bookingms.domain.model.aggregates.Shipper;
import com.example.bookingms.domain.model.valueobjects.ShipperProfile;
import com.example.bookingms.domain.model.valueobjects.ShipperType;
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
    public Optional<Shipper> findByEmail(EmailAddress email) {
        return Optional.ofNullable(mapper.findByEmail(email.value()))
                .map(MyBatisShipperRepository::toDomain);
    }

    @Override
    public Optional<Shipper> findById(Long id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(mapper.findById(id)).map(MyBatisShipperRepository::toDomain);
    }

    /**
     * 登録と編集のどちらも受ける。
     *
     * <p><strong>id を持つ荷主は更新する。</strong>常に INSERT すると、編集のつもりの操作で
     * 荷主が増える。しかも荷主コードを採番し直していたため、<strong>予約から見た荷主が
     * 別人になる</strong>（#550。IT3 で `Cargo` に同じ形の欠陥があった）。
     */
    @Override
    public Shipper save(Shipper shipper) {
        ShipperRecord row = new ShipperRecord();
        if (shipper.id() != null) {
            row.setId(shipper.id());
            row.setShipperCode(shipper.shipperCode());
        } else {
            // 採番はシーケンスに任せる。テストでも本番と同じ経路を通す
            row.setShipperCode("SHP-%06d".formatted(mapper.nextShipperCodeNumber()));
        }
        row.setShipperType(shipper.type().name());
        row.setName(shipper.name());
        row.setEmail(shipper.email().value());
        row.setAddress(shipper.address());
        row.setPhone(shipper.phone());
        row.setContractNumber(shipper.contractNumber().map(ContractNumber::value).orElse(null));
        row.setDiscountRate(shipper.discountRate().map(DiscountRate::rate).orElse(null));
        if (shipper.id() != null) {
            mapper.update(row);
        } else {
            mapper.insert(row);
        }
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
                ContractNumber.restore(row.getContractNumber()),
                row.getDiscountRate() == null
                        ? null
                        : DiscountRate.restore(row.getDiscountRate()));
    }

    private static Shipper toDomain(ShipperRecord row) {
        return Shipper.restore(
                row.getId(),
                row.getShipperCode(),
                ShipperType.valueOf(row.getShipperType()),
                ShipperProfile.restore(
                        row.getName(), row.getEmail(), row.getAddress(), row.getPhone()),
                // 復元では検査しない。列が無かったころの行が読めなくなる
                contractOf(row));
    }
}
