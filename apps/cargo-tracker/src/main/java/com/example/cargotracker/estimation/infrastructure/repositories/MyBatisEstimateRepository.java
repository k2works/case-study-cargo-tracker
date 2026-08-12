package com.example.cargotracker.estimation.infrastructure.repositories;

import com.example.cargotracker.estimation.domain.model.aggregates.Estimate;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimateId;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimationCargoType;
import com.example.cargotracker.estimation.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.estimation.domain.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
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
                new com.example.cargotracker.estimation.domain.model.valueobjects.EstimateSpecification(
                        Location.of(row.getOrigin()),
                        Location.of(row.getDestination()),
                        row.getArrivalDeadline(),
                        EstimationCargoType.valueOf(row.getCargoType()),
                        row.getWeightKg()),
                candidates,
                row.getNoCandidateReason() == null ? null
                        : com.example.cargotracker.estimation.domain.model.valueobjects.NoCandidateReason
                                .valueOf(row.getNoCandidateReason()),
                com.example.cargotracker.estimation.domain.model.valueobjects.HazardousDeclaration.of(
                        row.getHazardClass(), row.getUnNumber(), row.getProperShippingName()),
                row.getVersion()));
    }

    private static EstimateRecord toRecord(Estimate estimate) {
        EstimateRecord row = new EstimateRecord();
        row.setEstimateId(estimate.estimateId().value());
        row.setOrigin(estimate.origin().unlocode());
        row.setDestination(estimate.destination().unlocode());
        row.setArrivalDeadline(estimate.arrivalDeadline());
        row.setCargoType(estimate.cargoType().name());
        row.setWeightKg(estimate.weightKg());
        row.setNoCandidateReason(estimate.noCandidateReason() == null
                ? null : estimate.noCandidateReason().name());
        var hazardous = estimate.hazardousDeclaration();
        if (hazardous != null) {
            row.setHazardClass(hazardous.hazardClass());
            row.setUnNumber(hazardous.unNumber());
            row.setProperShippingName(hazardous.properShippingName());
        }
        return row;
    }

    private static RouteCandidateRecord toRecord(
            long estimateId, RouteCandidate candidate, int priority) {
        RouteCandidateRecord row = new RouteCandidateRecord();
        row.setEstimateId(estimateId);
        row.setVoyageNumber(candidate.voyageNumber());
        row.setTransitPort(candidate.transitPort());
        row.setTransitDays(candidate.transitDays());
        row.setEstimatedCostValue(candidate.estimatedCost());
        row.setEstimatedCostCurrency(candidate.currency());
        row.setPriority(priority);
        return row;
    }

    private static RouteCandidate toCandidate(RouteCandidateRecord row) {
        return new RouteCandidate(
                row.getVoyageNumber(),
                row.getTransitPort(),
                row.getTransitDays(),
                row.getEstimatedCostValue(),
                row.getEstimatedCostCurrency());
    }
}
