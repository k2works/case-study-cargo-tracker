package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.aggregates.Estimate;
import com.example.bookingms.domain.model.entities.RouteCandidate;
import com.example.bookingms.domain.ports.EstimateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積コマンドサービス
 */
@Service
public class EstimateCommandService {

    private final EstimateRepository estimateRepository;

    public EstimateCommandService(EstimateRepository estimateRepository) {
        this.estimateRepository = estimateRepository;
    }

    /**
     * 見積作成コマンド
     */
    public record CreateEstimateCommand(String originUnlocode, String destinationUnlocode,
                                         LocalDate arrivalDeadline, String cargoType,
                                         BigDecimal weightKg) {}

    /**
     * 見積を作成してルート候補を算出する
     */
    @Transactional
    public Estimate create(CreateEstimateCommand command) {
        Estimate estimate = Estimate.create(
                command.originUnlocode(),
                command.destinationUnlocode(),
                command.arrivalDeadline(),
                command.cargoType(),
                command.weightKg()
        );

        // IT10では簡易版として静的ルール2候補を返す
        List<RouteCandidate> candidates = generateCandidates(command);
        estimate.replaceCandidates(candidates);

        return estimateRepository.save(estimate);
    }

    /**
     * 簡易ルート候補を生成する（静的ルール）
     */
    private List<RouteCandidate> generateCandidates(CreateEstimateCommand command) {
        BigDecimal baseCost = command.weightKg().multiply(BigDecimal.valueOf(200));
        RouteCandidate direct = new RouteCandidate(
                "V001", null, 14,
                baseCost, 1);
        RouteCandidate viaPort = new RouteCandidate(
                "V002", "SGSIN", 18,
                baseCost.multiply(BigDecimal.valueOf(0.8)), 2);
        return List.of(direct, viaPort);
    }
}
