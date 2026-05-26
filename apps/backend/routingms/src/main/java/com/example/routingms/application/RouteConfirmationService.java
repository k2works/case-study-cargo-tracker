package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import com.example.shared.events.RouteConfirmedEvent;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 経路確定（cross-service 紐付け）アプリケーションサービス（US11）。
 *
 * <p>選択された経路候補を確定し、{@link RouteConfirmedEvent}（shared）を Axon の {@link EventGateway} に
 * 発行する。Axon Kafka Publisher が当該イベントを {@code cargo-events} トピックへ転送し、bookingms が
 * tracking 購読して予約状態を ROUTE_PROPOSED に更新する（ADR-0009、IT3 の逆方向 cross-service）。
 * 併せて経路設計依頼（route_design_request）の状態を {@code ASSIGNED}（割当済み）へ遷移させる。</p>
 */
@Service
public class RouteConfirmationService {

    static final String STATUS_ASSIGNED = "ASSIGNED";

    private final RouteCalculationService calculationService;
    private final RouteDesignRequestMapper requestMapper;
    private final EventGateway eventGateway;

    public RouteConfirmationService(RouteCalculationService calculationService,
                                    RouteDesignRequestMapper requestMapper,
                                    EventGateway eventGateway) {
        this.calculationService = calculationService;
        this.requestMapper = requestMapper;
        this.eventGateway = eventGateway;
    }

    /**
     * 選択した経路を確定し予約へ紐付ける（US11）。
     *
     * @param bookingId 予約 ID
     * @param sequence  推奨順の 1 始まりの候補番号
     * @return 確定した経路候補
     * @throws RouteDesignRequestNotFoundException 経路設計依頼が存在しない場合
     * @throws IllegalArgumentException            候補番号が範囲外（候補なしを含む）の場合
     */
    public RouteCandidate confirmRoute(String bookingId, int sequence) {
        List<RouteCandidate> candidates = calculationService.calculate(bookingId);
        RouteCandidate confirmed = RouteSelectionService.pickBySequence(candidates, sequence);
        eventGateway.publish(toEvent(bookingId, confirmed));
        requestMapper.updateStatus(bookingId, STATUS_ASSIGNED);
        return confirmed;
    }

    private RouteConfirmedEvent toEvent(String bookingId, RouteCandidate candidate) {
        List<RouteConfirmedEvent.LegData> legs = candidate.legs().stream()
                .map(leg -> new RouteConfirmedEvent.LegData(
                        leg.voyageNumber(),
                        leg.loadUnlocode(),
                        leg.unloadUnlocode(),
                        leg.loadTime(),
                        leg.unloadTime()))
                .toList();
        return new RouteConfirmedEvent(bookingId, legs);
    }
}
