package com.example.cargotracker.estimation.application;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.estimation.domain.model.repository.EstimateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 輸送見積アプリケーションサービス。
 * ルート候補はスタブ実装（固定値）で返す。
 */
@Service
@Transactional
public class EstimateService {

    private final EstimateRepository estimateRepository;

    public EstimateService(EstimateRepository estimateRepository) {
        this.estimateRepository = estimateRepository;
    }

    public EstimateId createEstimate(CreateEstimateCommand command) {
        Estimate estimate = Estimate.create(
                command.originUnlocode(),
                command.destinationUnlocode(),
                command.arrivalDeadline(),
                command.cargoType(),
                command.weightKg()
        );

        List<RouteCandidate> stubCandidates = generateStubCandidates(
                command.originUnlocode(), command.destinationUnlocode(), command.weightKg());
        estimate.addCandidates(stubCandidates);

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

    private List<RouteCandidate> generateStubCandidates(
            String origin, String destination, BigDecimal weightKg) {
        BigDecimal baseCost = weightKg.multiply(new BigDecimal("500"));
        return List.of(
                new RouteCandidate("V001", "SGSIN", 21, baseCost),
                new RouteCandidate("V002", "HKHKG", 28, baseCost.multiply(new BigDecimal("0.96")))
        );
    }
}
