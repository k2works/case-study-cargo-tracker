package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionQueryService;
import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionRequestView;
import com.example.cargotracker.handling.domain.model.CorrectionRequestType;
import com.example.cargotracker.handling.domain.model.CorrectionStatus;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link CorrectionQueryService} の MyBatis 実装（US36）。 */
@Service
public class MyBatisCorrectionQueryService implements CorrectionQueryService {

    private final CorrectionMapper mapper;
    private final HandlingMapper handlingMapper;

    public MyBatisCorrectionQueryService(
            CorrectionMapper mapper, HandlingMapper handlingMapper) {
        this.mapper = mapper;
        this.handlingMapper = handlingMapper;
    }

    @Override
    public List<CorrectionRequestView> findPending() {
        return mapper.findPending().stream().map(this::toView).toList();
    }

    @Override
    public List<CorrectionRequestView> findRecent(int limit) {
        return mapper.findRecent(limit).stream().map(this::toView).toList();
    }

    @Override
    public int countPending() {
        return mapper.countPending();
    }

    /**
     * 追跡番号を添える。
     *
     * <p><strong>承認者が手にしているのは追跡番号である。</strong> 申請 ID だけでは
     * どの貨物の話か分からず、1 件ずつ荷役を開くことになる。
     */
    private CorrectionRequestView toView(CorrectionRecord row) {
        HandlingActivityRecord activity = handlingMapper.findById(row.getHandlingActivityId());
        CorrectionStatus status = CorrectionStatus.valueOf(row.getStatus());
        return new CorrectionRequestView(
                row.getId(),
                activity == null || activity.getTrackingNumber() == null
                        ? "" : activity.getTrackingNumber(),
                CorrectionRequestType.valueOf(row.getRequestType()).displayName(),
                new CorrectionRequestView.Submission(
                        row.getReason(), row.getRequestedBy(), row.getRequestedAt()),
                new CorrectionRequestView.Decision(
                        status.displayName(),
                        status.badgeClass(),
                        row.getDecidedBy(),
                        row.getDecidedAt(),
                        row.getDecisionReason()));
    }
}
