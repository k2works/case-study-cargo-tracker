package com.example.routingms.infrastructure.repositories;

import com.example.routingms.domain.repository.LocationRepository;
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
        return mapper.findAll().stream()
                .map(row -> Location.of(row.getUnlocode(), row.getName()))
                .toList();
    }

    @Override
    public Optional<Location> findByUnLocode(String unLocode) {
        return Optional.ofNullable(mapper.findByUnLocode(unLocode))
                .map(row -> Location.of(row.getUnlocode(), row.getName()));
    }

    @Override
    public Optional<ZoneId> timeZoneOf(String unLocode) {
        return Optional.ofNullable(mapper.findTimeZoneOf(unLocode)).map(ZoneId::of);
    }
}
