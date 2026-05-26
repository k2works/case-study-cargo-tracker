package com.example.routingms.application;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 経路選択確定アプリケーションサービス（US09）。
 *
 * <p>算出済みの経路候補から 1 件を選択し、経路設計依頼（route_design_request）の状態を
 * {@code ROUTE_SELECTED}（選択済み）へ遷移させる。経路候補は永続化しないため、選択時に再算出して
 * 指定番号の候補を検証・返却する（M4：route_design_request の状態遷移を導入）。</p>
 */
@Service
public class RouteSelectionService {

    static final String STATUS_ROUTE_SELECTED = "ROUTE_SELECTED";

    private final RouteCalculationService calculationService;
    private final RouteDesignRequestMapper requestMapper;

    public RouteSelectionService(RouteCalculationService calculationService,
                                 RouteDesignRequestMapper requestMapper) {
        this.calculationService = calculationService;
        this.requestMapper = requestMapper;
    }

    /**
     * 経路候補を選択して確定する（US09）。
     *
     * @param bookingId 予約 ID
     * @param sequence  推奨順の 1 始まりの候補番号
     * @return 選択された経路候補
     * @throws RouteDesignRequestNotFoundException 経路設計依頼が存在しない場合
     * @throws IllegalArgumentException            候補番号が範囲外（候補なしを含む）の場合
     */
    public RouteCandidate selectRoute(String bookingId, int sequence) {
        List<RouteCandidate> candidates = calculationService.calculate(bookingId);
        RouteCandidate selected = pickBySequence(candidates, sequence);
        requestMapper.updateStatus(bookingId, STATUS_ROUTE_SELECTED);
        return selected;
    }

    static RouteCandidate pickBySequence(List<RouteCandidate> candidates, int sequence) {
        if (sequence < 1 || sequence > candidates.size()) {
            throw new IllegalArgumentException(
                    "選択できる経路候補がありません（候補番号: " + sequence + "、候補数: " + candidates.size() + "）");
        }
        return candidates.get(sequence - 1);
    }
}
