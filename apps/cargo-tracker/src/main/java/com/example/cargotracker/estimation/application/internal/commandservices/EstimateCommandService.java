package com.example.cargotracker.estimation.application.internal.commandservices;

import com.example.cargotracker.estimation.domain.model.CargoType;
import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 輸送見積アプリケーションサービス。
 * ルート候補はスタブ実装（固定値）で返す。
 * TODO: 外部ルーティングサービスとの連携時に置換予定
 */
@Service
@Transactional
public class EstimateCommandService {

    private final EstimateRepository estimateRepository;

    public EstimateCommandService(EstimateRepository estimateRepository) {
        this.estimateRepository = estimateRepository;
    }

    public EstimateId createEstimate(CreateEstimateCommand command) {
        Estimate estimate = Estimate.create(
                new Location(command.originUnlocode()),
                new Location(command.destinationUnlocode()),
                command.arrivalDeadline(),
                CargoType.valueOf(command.cargoType()),
                command.weightKg()
        );

        List<RouteCandidate> stubCandidates = generateStubCandidates(command.weightKg());
        estimate.replaceCandidates(stubCandidates);

        estimateRepository.save(estimate);
        return estimate.getEstimateId();
    }

    @Transactional(readOnly = true)
    public Optional<Estimate> findByEstimateId(EstimateId estimateId) {
        return estimateRepository.findByEstimateId(estimateId);
    }

    @Transactional(readOnly = true)
    public List<Estimate> findAll() {
        return estimateRepository.findAll();
    }

    private List<RouteCandidate> generateStubCandidates(BigDecimal weightKg) {
        BigDecimal baseCost = weightKg.multiply(new BigDecimal("500"));
        return List.of(
                new RouteCandidate("V001", "SGSIN", 21, baseCost),
                new RouteCandidate("V002", "HKHKG", 28, baseCost.multiply(new BigDecimal("0.96")))
        );
    }
}
