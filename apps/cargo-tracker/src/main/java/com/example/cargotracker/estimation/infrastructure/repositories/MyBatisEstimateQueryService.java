package com.example.cargotracker.estimation.infrastructure.repositories;

import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import com.example.cargotracker.estimation.application.internal.queryservices
        .EstimateSummaryView;
import com.example.cargotracker.estimation.domain.model.EstimateStatus;
import com.example.cargotracker.estimation.domain.model.EstimationCargoType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link EstimateQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisEstimateQueryService implements EstimateQueryService {

    private final EstimateQueryMapper mapper;

    /** <strong>「今日」は業務のタイムゾーンで決める。</strong> UTC で数えると境目がずれる。 */
    private final Clock clock;

    public MyBatisEstimateQueryService(EstimateQueryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
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
