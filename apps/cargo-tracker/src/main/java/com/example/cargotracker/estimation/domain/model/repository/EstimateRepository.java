package com.example.cargotracker.estimation.domain.model.repository;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;

import java.util.List;
import java.util.Optional;

public interface EstimateRepository {

    void save(Estimate estimate);

    Optional<Estimate> findByEstimateId(EstimateId estimateId);

    List<Estimate> findAll();
}
