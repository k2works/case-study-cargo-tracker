package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Estimate;
import com.example.bookingms.domain.model.entities.RouteCandidate;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.EstimateStatus;
import com.example.bookingms.domain.ports.EstimateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MyBatis を使用した見積リポジトリ実装
 */
@Repository
public class MyBatisEstimateRepository implements EstimateRepository {

    private final EstimateMapper estimateMapper;

    public MyBatisEstimateRepository(EstimateMapper estimateMapper) {
        this.estimateMapper = estimateMapper;
    }

    @Override
    public Estimate save(Estimate estimate) {
        EstimateRecord estimateRecord = toRecord(estimate);
        estimateMapper.insertEstimate(estimateRecord);

        for (RouteCandidate candidate : estimate.getCandidates()) {
            RouteCandidateRecord candidateRecord = toCandidateRecord(candidate, estimateRecord.getId());
            estimateMapper.insertRouteCandidate(candidateRecord);
        }

        return new Estimate(
                estimateRecord.getId(),
                estimate.getEstimateId(),
                estimate.getOriginUnlocode(),
                estimate.getDestinationUnlocode(),
                estimate.getArrivalDeadline(),
                estimate.getCargoType(),
                estimate.getWeightKg(),
                estimate.getStatus(),
                estimate.getCandidates()
        );
    }

    @Override
    public Optional<Estimate> findByEstimateId(UUID estimateId) {
        return estimateMapper.findByEstimateId(estimateId.toString()).map(estimateRecord -> {
            List<RouteCandidateRecord> candidateRecords =
                    estimateMapper.findCandidatesByEstimateDbId(estimateRecord.getId());
            List<RouteCandidate> candidates = candidateRecords.stream()
                    .map(this::toCandidateDomain)
                    .collect(Collectors.toList());
            return new Estimate(
                    estimateRecord.getId(),
                    UUID.fromString(estimateRecord.getEstimateId()),
                    estimateRecord.getOriginUnlocode(),
                    estimateRecord.getDestinationUnlocode(),
                    estimateRecord.getArrivalDeadline(),
                    CargoType.valueOf(estimateRecord.getCargoType()),
                    estimateRecord.getWeightKg(),
                    EstimateStatus.valueOf(estimateRecord.getStatus()),
                    candidates
            );
        });
    }

    private EstimateRecord toRecord(Estimate estimate) {
        EstimateRecord estimateRecord = new EstimateRecord();
        estimateRecord.setEstimateId(estimate.getEstimateId().toString());
        estimateRecord.setOriginUnlocode(estimate.getOriginUnlocode());
        estimateRecord.setDestinationUnlocode(estimate.getDestinationUnlocode());
        estimateRecord.setArrivalDeadline(estimate.getArrivalDeadline());
        estimateRecord.setCargoType(estimate.getCargoType().name());
        estimateRecord.setWeightKg(estimate.getWeightKg());
        estimateRecord.setStatus(estimate.getStatus().name());
        return estimateRecord;
    }

    private RouteCandidateRecord toCandidateRecord(RouteCandidate candidate, Long estimateDbId) {
        RouteCandidateRecord candidateRecord = new RouteCandidateRecord();
        candidateRecord.setEstimateId(estimateDbId);
        candidateRecord.setVoyageNumber(candidate.getVoyageNumber());
        candidateRecord.setTransitPort(candidate.getTransitPort());
        candidateRecord.setTransitDays(candidate.getTransitDays());
        candidateRecord.setEstimatedCost(candidate.getEstimatedCost());
        candidateRecord.setRank(candidate.getRank());
        return candidateRecord;
    }

    private RouteCandidate toCandidateDomain(RouteCandidateRecord candidateRecord) {
        return new RouteCandidate(
                candidateRecord.getVoyageNumber(),
                candidateRecord.getTransitPort(),
                candidateRecord.getTransitDays(),
                candidateRecord.getEstimatedCost(),
                candidateRecord.getRank()
        );
    }
}
