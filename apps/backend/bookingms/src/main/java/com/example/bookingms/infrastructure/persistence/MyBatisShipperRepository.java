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
        ShipperRecord row = new ShipperRecord();
        // 採番はシーケンスに任せる。テストでも本番と同じ経路を通す
        row.setShipperCode("SHP-%06d".formatted(mapper.nextShipperCodeNumber()));
        row.setShipperType(shipper.type().name());
        row.setName(shipper.name());
        row.setEmail(shipper.email());
        row.setAddress(shipper.address());
        row.setPhone(shipper.phone());
        mapper.insert(row);
        return toDomain(row);
    }

    @Override
    public List<Shipper> search(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.search(normalized).stream().map(MyBatisShipperRepository::toDomain).toList();
    }

    private static Shipper toDomain(ShipperRecord row) {
        return Shipper.restore(
                row.getId(),
                row.getShipperCode(),
                ShipperType.valueOf(row.getShipperType()),
                row.getName(),
                row.getEmail(),
                row.getAddress(),
                row.getPhone());
    }
}
