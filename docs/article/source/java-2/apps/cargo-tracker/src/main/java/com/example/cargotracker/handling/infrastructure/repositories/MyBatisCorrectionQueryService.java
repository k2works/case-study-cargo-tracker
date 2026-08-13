package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionQueryService;
import com.example.cargotracker.handling.application.internal.queryservices
        .CorrectionRequestView;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionRequestType;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return toViews(mapper.findPending());
    }

    @Override
    public List<CorrectionRequestView> findRecent(int limit) {
        return toViews(mapper.findRecent(limit));
    }

    /**
     * 一覧を組み立てる。
     *
     * <p><strong>追跡番号はまとめて引く</strong>（R5）。1 行ごとに荷役を開くと、
     * <strong>待ち行列が伸びるほど遅くなる</strong> — いちばん混んでいるときに、
     * いちばん遅い。
     */
    private List<CorrectionRequestView> toViews(List<CorrectionRecord> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> trackingNumbers = trackingNumbersOf(rows);
        return rows.stream()
                .map(row -> toView(row, trackingNumbers.get(row.getHandlingActivityId())))
                .toList();
    }

    private Map<Long, String> trackingNumbersOf(List<CorrectionRecord> rows) {
        List<Long> ids = rows.stream()
                .map(CorrectionRecord::getHandlingActivityId)
                .distinct()
                .toList();
        Map<Long, String> found = new LinkedHashMap<>();
        for (HandlingTrackingNumberRow row : handlingMapper.findTrackingNumbersByIds(ids)) {
            found.put(row.getId(), row.getTrackingNumber());
        }
        return found;
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
    private CorrectionRequestView toView(CorrectionRecord row, String trackingNumber) {
        CorrectionStatus status = CorrectionStatus.valueOf(row.getStatus());
        return new CorrectionRequestView(
                row.getId(),
                trackingNumber == null ? "" : trackingNumber,
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
