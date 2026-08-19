package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.ShipperRepository;
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
    public Shipper save(Shipper shipper) {
        ShipperRecord record = new ShipperRecord();
        // 採番はシーケンスに任せる。テストでも本番と同じ経路を通す
        record.setShipperCode("SHP-%06d".formatted(mapper.nextShipperCodeNumber()));
        record.setShipperType(shipper.type().name());
        record.setName(shipper.name());
        record.setEmail(shipper.email());
        record.setAddress(shipper.address());
        record.setPhone(shipper.phone());
        mapper.insert(record);
        return toDomain(record);
    }

    @Override
    public List<Shipper> search(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.search(normalized).stream().map(MyBatisShipperRepository::toDomain).toList();
    }

    private static Shipper toDomain(ShipperRecord record) {
        return Shipper.restore(
                record.getId(),
                record.getShipperCode(),
                ShipperType.valueOf(record.getShipperType()),
                record.getName(),
                record.getEmail(),
                record.getAddress(),
                record.getPhone());
    }
}
