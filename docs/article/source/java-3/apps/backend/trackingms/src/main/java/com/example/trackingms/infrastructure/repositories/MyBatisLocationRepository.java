package com.example.trackingms.infrastructure.repositories;

import com.example.shared.domain.model.Location;
import com.example.trackingms.domain.repository.LocationRepository;
import java.util.Optional;

/** 地点マスタの複製（[ADR-014]）。 */
public class MyBatisLocationRepository implements LocationRepository {

    private final LocationMapper mapper;

    public MyBatisLocationRepository(LocationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Location> findByUnLocode(String unLocode) {
        return unLocode == null ? Optional.empty()
                : Optional.ofNullable(mapper.findByUnLocode(unLocode));
    }
}
