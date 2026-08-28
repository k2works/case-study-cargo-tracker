package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.repository.LocationRepository;
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
    public java.util.Map<String, String> regionsByUnLocode() {
        // 地点マスタは 10 件程度である。1 件ずつ引くより 1 回で読む
        return mapper.findAll().stream().collect(java.util.stream.Collectors.toMap(
                LocationRecord::getUnlocode, LocationRecord::getRegion));
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
