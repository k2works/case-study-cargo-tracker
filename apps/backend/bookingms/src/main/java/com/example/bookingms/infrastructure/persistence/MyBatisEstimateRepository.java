package com.example.bookingms.infrastructure.persistence;

import com.example.bookingms.application.port.EstimateRepository;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Estimate;
import com.example.bookingms.domain.model.EstimateId;
import com.example.bookingms.domain.model.EstimateNumber;
import com.example.bookingms.domain.model.EstimateStatus;
import com.example.bookingms.domain.model.RouteCandidate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 見積の永続化（US01）。
 *
 * <p><strong>常に新規の書き込みである。</strong>見積は作ったあと動かない
 * （期限切れの扱いは別のストーリー）。
 */
public class MyBatisEstimateRepository implements EstimateRepository {

    private final EstimateMapper estimates;

    private final Clock clock;

    public MyBatisEstimateRepository(EstimateMapper estimates, Clock clock) {
        this.estimates = estimates;
        this.clock = clock;
    }

    @Override
    public void save(Estimate estimate) {
        EstimateRecord row = new EstimateRecord();
        row.setEstimateId(estimate.estimateId().value().toString());
        row.setEstimateNumber(estimate.estimateNumber().value());
        row.setOriginUnlocode(estimate.originUnLocode());
        row.setDestinationUnlocode(estimate.destinationUnLocode());
        row.setArrivalDeadline(estimate.arrivalDeadline());
        row.setCargoType(estimate.cargoType().name());
        row.setWeightKg(estimate.weightKg());
        row.setStatus(estimate.status().name());
        estimates.insert(row);

        // **候補ごと保存する。**落とすと、開き直したときに荷主へ出した数字が消える
        int rank = 0;
        for (RouteCandidate candidate : estimate.candidates()) {
            RouteCandidateRecord candidateRow = new RouteCandidateRecord();
            candidateRow.setEstimateId(row.getId());
            candidateRow.setVoyageNumber(candidate.voyageNumber());
            candidateRow.setTransitPort(candidate.transitPort());
            candidateRow.setTransitDays(candidate.transitDays());
            candidateRow.setEstimatedCost(candidate.estimatedCost());
            candidateRow.setRank(rank++);
            estimates.insertCandidate(candidateRow);
        }
    }

    @Override
    public Optional<Estimate> findById(String estimateId) {
        return Optional.ofNullable(estimates.selectByEstimateId(estimateId))
                .map(row -> toDomain(row, estimates.selectCandidates(row.getEstimateId())));
    }

    @Override
    public Optional<Estimate> findByNumber(String estimateNumber) {
        return Optional.ofNullable(estimates.selectByEstimateNumber(estimateNumber))
                .map(row -> toDomain(row, estimates.selectCandidates(row.getEstimateId())));
    }

    @Override
    public List<Estimate> findAll() {
        return estimates.selectAll().stream()
                .map(row -> toDomain(row, estimates.selectCandidates(row.getEstimateId())))
                .toList();
    }

    @Override
    public EstimateNumber nextNumber() {
        // **年は業務の暦で決める**（[ADR-011]）。UTC だと年末年始の数時間だけ前年になる
        int year = LocalDate.now(clock).getYear();
        return EstimateNumber.of(
                "EST-%d%06d".formatted(year, estimates.nextEstimateNumber()));
    }

    private static Estimate toDomain(EstimateRecord row, List<RouteCandidateRecord> candidates) {
        // **復元では検査しない**（新しい不変条件は既存行を壊す）
        return Estimate.restore(
                EstimateId.of(row.getEstimateId()),
                EstimateNumber.of(row.getEstimateNumber()),
                new com.example.bookingms.domain.model.EstimateRequirements(
                        row.getOriginUnlocode(), row.getDestinationUnlocode(),
                        row.getArrivalDeadline(), CargoType.valueOf(row.getCargoType()),
                        row.getWeightKg()),
                candidates.stream()
                        .map(candidate -> new RouteCandidate(candidate.getVoyageNumber(),
                                candidate.getTransitPort(), candidate.getTransitDays(),
                                candidate.getEstimatedCost()))
                        .toList(),
                EstimateStatus.valueOf(row.getStatus()));
    }
}
