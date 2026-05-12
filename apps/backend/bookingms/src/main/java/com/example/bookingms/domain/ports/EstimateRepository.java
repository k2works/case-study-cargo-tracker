package com.example.bookingms.domain.ports;

import com.example.bookingms.domain.model.aggregates.Estimate;

import java.util.Optional;
import java.util.UUID;

public interface EstimateRepository {
    Estimate save(Estimate estimate);
    Optional<Estimate> findByEstimateId(UUID estimateId);
}
