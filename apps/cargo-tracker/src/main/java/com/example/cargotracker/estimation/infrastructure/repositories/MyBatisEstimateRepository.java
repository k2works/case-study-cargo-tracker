package com.example.cargotracker.estimation.infrastructure.repositories;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.EstimationCargoType;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link EstimateRepository} の MyBatis 実装。 */
@Repository
public class MyBatisEstimateRepository implements EstimateRepository {

    private final EstimateMapper mapper;

    public MyBatisEstimateRepository(EstimateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void save(Estimate estimate) {
        mapper.insert(toRecord(estimate));
        EstimateRecord saved = mapper.findByEstimateId(estimate.estimateId().value());
        int priority = 0;
        for (RouteCandidate candidate : estimate.candidates()) {
            mapper.insertCandidate(toRecord(saved.getId(), candidate, priority++));
        }
        // **版を進めてよいのは、保存が成功したことを知っているここだけである**（IT15 の C4）
        estimate.markPersisted();
    }

    @Override
    public Optional<Estimate> findByEstimateId(EstimateId estimateId) {
        EstimateRecord row = mapper.findByEstimateId(estimateId.value());
        if (row == null) {
            return Optional.empty();
        }
        List<RouteCandidate> candidates = mapper.findCandidates(row.getId()).stream()
                .map(MyBatisEstimateRepository::toCandidate)
                .toList();
        return Optional.of(Estimate.reconstruct(
                new EstimateId(row.getEstimateId()),
                new com.example.cargotracker.estimation.domain.model.EstimateSpecification(
                        Location.of(row.getOrigin()),
                        Location.of(row.getDestination()),
                        row.getArrivalDeadline(),
                        EstimationCargoType.valueOf(row.getCargoType()),
                        row.getWeightKg()),
                candidates,
                row.getNoCandidateReason() == null ? null
                        : com.example.cargotracker.estimation.domain.model.NoCandidateReason
                                .valueOf(row.getNoCandidateReason()),
                row.getVersion()));
    }

    private static EstimateRecord toRecord(Estimate estimate) {
        EstimateRecord record = new EstimateRecord();
        record.setEstimateId(estimate.estimateId().value());
        record.setOrigin(estimate.origin().unlocode());
        record.setDestination(estimate.destination().unlocode());
        record.setArrivalDeadline(estimate.arrivalDeadline());
        record.setCargoType(estimate.cargoType().name());
        record.setWeightKg(estimate.weightKg());
        record.setNoCandidateReason(estimate.noCandidateReason() == null
                ? null : estimate.noCandidateReason().name());
        return record;
    }

    private static RouteCandidateRecord toRecord(
            long estimateId, RouteCandidate candidate, int priority) {
        RouteCandidateRecord record = new RouteCandidateRecord();
        record.setEstimateId(estimateId);
        record.setVoyageNumber(candidate.voyageNumber());
        record.setTransitPort(candidate.transitPort());
        record.setTransitDays(candidate.transitDays());
        record.setEstimatedCostValue(candidate.estimatedCost());
        record.setEstimatedCostCurrency(candidate.currency());
        record.setPriority(priority);
        return record;
    }

    private static RouteCandidate toCandidate(RouteCandidateRecord record) {
        return new RouteCandidate(
                record.getVoyageNumber(),
                record.getTransitPort(),
                record.getTransitDays(),
                record.getEstimatedCostValue(),
                record.getEstimatedCostCurrency());
    }
}
