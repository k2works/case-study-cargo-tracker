package com.example.cargotracker.estimation.infrastructure.repositories;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.EstimateStatus;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.repository.EstimateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisEstimateRepository implements EstimateRepository {

    private final EstimateMapper estimateMapper;

    public MyBatisEstimateRepository(EstimateMapper estimateMapper) {
        this.estimateMapper = estimateMapper;
    }

    @Override
    public void save(Estimate estimate) {
        EstimateRecord record = toRecord(estimate);
        estimateMapper.insertEstimate(record);

        if (!estimate.getCandidates().isEmpty()) {
            List<RouteCandidateRecord> candidateRecords = toCandidateRecords(estimate.getCandidates());
            estimateMapper.insertRouteCandidates(record.id, candidateRecords);
        }
    }

    @Override
    public Optional<Estimate> findByEstimateId(EstimateId estimateId) {
        EstimateRecord record = estimateMapper.findEstimateByEstimateId(estimateId.toString());
        if (record == null) {
            return Optional.empty();
        }
        List<RouteCandidateRecord> candidateRecords = estimateMapper.findCandidatesByEstimateId(record.id);
        return Optional.of(toEstimate(record, candidateRecords));
    }

    @Override
    public List<Estimate> findAll() {
        List<EstimateRecord> records = estimateMapper.findAllEstimates();
        return records.stream()
                .map(r -> {
                    List<RouteCandidateRecord> candidates = estimateMapper.findCandidatesByEstimateId(r.id);
                    return toEstimate(r, candidates);
                })
                .toList();
    }

    private EstimateRecord toRecord(Estimate estimate) {
        EstimateRecord r = new EstimateRecord();
        r.estimateId = estimate.getEstimateId().toString();
        r.originUnlocode = estimate.getOriginUnlocode();
        r.destinationUnlocode = estimate.getDestinationUnlocode();
        r.arrivalDeadline = estimate.getArrivalDeadline();
        r.cargoType = estimate.getCargoType();
        r.weightKg = estimate.getWeightKg();
        r.status = estimate.getStatus().name();
        return r;
    }

    private List<RouteCandidateRecord> toCandidateRecords(List<RouteCandidate> candidates) {
        return candidates.stream().map(c -> {
            RouteCandidateRecord r = new RouteCandidateRecord();
            r.voyageNumber = c.voyageNumber();
            r.transitPort = c.transitPort();
            r.transitDays = c.transitDays();
            r.estimatedCost = c.estimatedCost();
            return r;
        }).toList();
    }

    private Estimate toEstimate(EstimateRecord r, List<RouteCandidateRecord> candidateRecords) {
        List<RouteCandidate> candidates = candidateRecords.stream()
                .map(c -> new RouteCandidate(c.voyageNumber, c.transitPort, c.transitDays, c.estimatedCost))
                .toList();
        return Estimate.reconstruct(
                new EstimateId(UUID.fromString(r.estimateId)),
                r.originUnlocode,
                r.destinationUnlocode,
                r.arrivalDeadline,
                r.cargoType,
                r.weightKg,
                EstimateStatus.valueOf(r.status),
                candidates
        );
    }
}
