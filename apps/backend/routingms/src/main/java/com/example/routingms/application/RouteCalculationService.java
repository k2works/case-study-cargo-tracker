package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.domain.services.OptimalRouteService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 経路候補算出アプリケーションサービス（US08）。
 *
 * <p>経路設計依頼（route_design_request Read Model）から探索制約を組み立て、航海スケジュール
 * 一覧を取得して {@link OptimalRouteService} に委譲する。経路候補は永続化せず算出結果として返す
 * （iteration_plan-4.md / ADR-0009）。条件調整（US10）では到着期限を上書きして再算出する。</p>
 */
@Service
public class RouteCalculationService {

    private final RouteDesignRequestQueryService requestQueryService;
    private final VoyageQueryService voyageQueryService;
    private final OptimalRouteService optimalRouteService;

    public RouteCalculationService(RouteDesignRequestQueryService requestQueryService,
                                   VoyageQueryService voyageQueryService,
                                   OptimalRouteService optimalRouteService) {
        this.requestQueryService = requestQueryService;
        this.voyageQueryService = voyageQueryService;
        this.optimalRouteService = optimalRouteService;
    }

    /**
     * 経路設計依頼に登録された制約条件で経路候補を算出する（US08）。
     *
     * @param bookingId 予約 ID
     * @return 推奨順の経路候補。該当なしの場合は空リスト
     * @throws RouteDesignRequestNotFoundException 経路設計依頼が存在しない場合
     */
    public List<RouteCandidate> calculate(String bookingId) {
        return calculate(bookingId, null);
    }

    /**
     * 到着期限を上書きして経路候補を再算出する（US10 条件調整）。
     *
     * @param bookingId          予約 ID
     * @param overrideDeadline   上書きする到着期限（{@code null} の場合は依頼の期限を用いる）
     * @return 推奨順の経路候補
     * @throws RouteDesignRequestNotFoundException 経路設計依頼が存在しない場合
     */
    public List<RouteCandidate> calculate(String bookingId, LocalDate overrideDeadline) {
        RouteDesignRequestProjection request = requestQueryService.findByBookingId(bookingId);
        if (request == null) {
            throw new RouteDesignRequestNotFoundException(bookingId);
        }
        LocalDate deadline = overrideDeadline != null ? overrideDeadline : request.getArrivalDeadline();
        RouteSearchSpecification spec = new RouteSearchSpecification(
                request.getOriginUnlocode(),
                request.getDestinationUnlocode(),
                deadline,
                request.getCargoType());
        List<VoyageProjection> voyages = voyageQueryService.findAll();
        return optimalRouteService.calculate(spec, voyages);
    }
}
