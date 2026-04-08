package com.example.cargotracker.estimation.application.internal.commandservices;

import com.example.cargotracker.estimation.domain.model.CargoType;
import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.port.RouteCandidateProvider;
import com.example.cargotracker.estimation.domain.model.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 輸送見積アプリケーションサービス。
 * ルート候補の算出は RouteCandidateProvider ポートに委譲する（依存性逆転）。
 */
@Service
@Transactional
public class EstimateCommandService {

    private final EstimateRepository estimateRepository;
    private final RouteCandidateProvider routeCandidateProvider;

    public EstimateCommandService(EstimateRepository estimateRepository,
                                  RouteCandidateProvider routeCandidateProvider) {
        this.estimateRepository = estimateRepository;
        this.routeCandidateProvider = routeCandidateProvider;
    }

    public EstimateId createEstimate(CreateEstimateCommand command) {
        Location origin = new Location(command.originUnlocode());
        Location destination = new Location(command.destinationUnlocode());
        CargoType cargoType = CargoType.valueOf(command.cargoType());

        Estimate estimate = Estimate.create(
                origin,
                destination,
                command.arrivalDeadline(),
                cargoType,
                command.weightKg()
        );

        List<RouteCandidate> candidates = routeCandidateProvider.findCandidates(
                origin, destination, command.arrivalDeadline(), cargoType
        );
        estimate.replaceCandidates(candidates);

        estimateRepository.save(estimate);
        return estimate.getEstimateId();
    }

    public void markAsBooked(String estimateIdValue) {
        EstimateId estimateId = new EstimateId(java.util.UUID.fromString(estimateIdValue));
        Estimate estimate = estimateRepository.findByEstimateId(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("見積が見つかりません: " + estimateIdValue));
        estimate.markAsBooked();
        estimateRepository.updateStatus(estimate);
    }

    @Transactional(readOnly = true)
    public Optional<Estimate> findByEstimateId(EstimateId estimateId) {
        return estimateRepository.findByEstimateId(estimateId);
    }

    @Transactional(readOnly = true)
    public List<Estimate> findAll() {
        return estimateRepository.findAll();
    }
}
