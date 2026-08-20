package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.LocationRepository;
import com.example.shared.domain.model.Location;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisLocationRepository implements LocationRepository {

    private final LocationMapper mapper;

    public MyBatisLocationRepository(LocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Location> findAll() {
        return mapper.findAll().stream().map(MyBatisLocationRepository::toDomain).toList();
    }

    @Override
    public Optional<Location> findByUnLocode(String unLocode) {
        return row(unLocode).map(MyBatisLocationRepository::toDomain);
    }

    @Override
    public Optional<ZoneId> timeZoneOf(String unLocode) {
        return row(unLocode).map(LocationRecord::getTimeZone).map(ZoneId::of);
    }

    private Optional<LocationRecord> row(String unLocode) {
        return unLocode == null ? Optional.empty()
                : Optional.ofNullable(mapper.findByUnLocode(unLocode));
    }

    private static Location toDomain(LocationRecord row) {
        return Location.of(row.getUnlocode(), row.getName());
    }
}
