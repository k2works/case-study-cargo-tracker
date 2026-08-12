package com.example.cargotracker.estimation.infrastructure.repositories;

import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import com.example.cargotracker.estimation.application.internal.queryservices
        .EstimateDetailView;
import com.example.cargotracker.estimation.application.internal.queryservices
        .EstimateSummaryView;
import com.example.cargotracker.estimation.domain.model.aggregates.Estimate;
import com.example.cargotracker.estimation.domain.model.aggregates.EstimateId;
import com.example.cargotracker.estimation.domain.repository.EstimateRepository;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimateStatus;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimationCargoType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link EstimateQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisEstimateQueryService implements EstimateQueryService {

    private final EstimateQueryMapper mapper;
    private final EstimateRepository repository;

    /** <strong>「今日」は業務のタイムゾーンで決める。</strong> UTC で数えると境目がずれる。 */
    private final Clock clock;

    public MyBatisEstimateQueryService(
            EstimateQueryMapper mapper, EstimateRepository repository, Clock clock) {
        this.mapper = mapper;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public java.util.Optional<EstimateDetailView> findById(String estimateId) {
        EstimateId id;
        try {
            id = EstimateId.of(estimateId);
        } catch (IllegalArgumentException e) {
            // **catch は解析だけを囲む**（IT15 の P2）。読み出しまで囲むと原因が消える
            return java.util.Optional.empty();
        }
        return repository.findByEstimateId(id).map(this::toDetail);
    }

    private EstimateDetailView toDetail(Estimate estimate) {
        var status = estimate.statusAsOf(LocalDate.now(clock));
        return new EstimateDetailView(
                estimate.estimateId().toString(),
                new EstimateSummaryView.Route(
                        estimate.origin().unlocode(), estimate.destination().unlocode()),
                new EstimateSummaryView.Cargo(
                        estimate.cargoType().name(),
                        estimate.cargoType().displayName(),
                        estimate.weightKg()),
                estimate.arrivalDeadline(),
                new EstimateSummaryView.Status(
                        status.displayName(), status.badgeClass(),
                        status == EstimateStatus.EXPIRED),
                new EstimateDetailView.Result(
                        estimate.candidates().stream()
                                .map(c -> new EstimateDetailView.Candidate(
                                        c.voyageNumber(),
                                        c.isDirect() ? "直行" : c.transitPort(),
                                        c.transitDays(),
                                        c.estimatedCost(),
                                        c.currency()))
                                .toList(),
                        // **気づく手段は次の行動へ繋ぐ。** 「ありません」だけでは、
                        // 読んだ人は次に何をすればよいか分からない
                        estimate.noCandidateReason() == null
                                ? "" : estimate.noCandidateReason().message()),
                estimate.hazardousDeclaration() == null ? null
                        : new EstimateDetailView.Hazardous(
                                estimate.hazardousDeclaration().hazardClass(),
                                estimate.hazardousDeclaration().unNumber(),
                                estimate.hazardousDeclaration().properShippingName()));
    }

    @Override
    public List<EstimateSummaryView> findAll() {
        LocalDate today = LocalDate.now(clock);
        return mapper.findAll().stream()
                .map(row -> toView(row, today, clock.getZone()))
                .toList();
    }

    private static EstimateSummaryView toView(
            EstimateQueryRow row, LocalDate today, java.time.ZoneId zone) {
        // **期限切れは読み出しのたびに判定する**（ビジネスルール 7。ADR-019 と同じ形）
        EstimateStatus status = EstimateStatus.asOf(row.getArrivalDeadline(), today);
        return new EstimateSummaryView(
                row.getEstimateId(),
                new EstimateSummaryView.Route(row.getOrigin(), row.getDestination()),
                new EstimateSummaryView.Cargo(
                        row.getCargoType(),
                        EstimationCargoType.valueOf(row.getCargoType()).displayName(),
                        row.getWeightKg()),
                row.getArrivalDeadline(),
                row.getCheapestCost(),
                new EstimateSummaryView.Status(
                        status.displayName(), status.badgeClass(),
                        status == EstimateStatus.EXPIRED),
                // **作成日も業務のタイムゾーンで出す。** UTC で切ると、
                // 夜に作った見積が前日の分として並ぶ
                row.getCreatedAt() == null
                        ? null : row.getCreatedAt().atZone(zone).toLocalDate());
    }
}
