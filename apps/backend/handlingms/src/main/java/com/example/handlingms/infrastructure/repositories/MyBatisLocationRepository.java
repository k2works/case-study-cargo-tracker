package com.example.handlingms.infrastructure.repositories;

import com.example.handlingms.domain.repository.LocationRepository;
import com.example.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;

/** 地点マスタ（MyBatis）。 */
public class MyBatisLocationRepository implements LocationRepository {

    private final LocationMapper mapper;

    public MyBatisLocationRepository(LocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Location> findByUnLocode(String unLocode) {
        return Optional.ofNullable(mapper.findByUnLocode(unLocode));
    }

    @Override
    public List<Location> findAll() {
        return mapper.findAll();
    }
}
