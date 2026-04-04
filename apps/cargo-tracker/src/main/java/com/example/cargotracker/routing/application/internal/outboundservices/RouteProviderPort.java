package com.example.cargotracker.routing.application.internal.outboundservices;

import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;

import java.util.List;

/**
 * 外部ルートプロバイダーへのポートインターフェース。
 *
 * <p>実際のルート検索ロジック（外部 API 呼び出しや別マイクロサービスへの委譲）は
 * このインターフェースの実装クラスが担う。
 *
 * <p>実装はフィルタリングを行わず、指定区間に就航する全航海をルート候補として返す。
 * 希望着日・貨物種別による絞り込みは呼び出し元の
 * {@link com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService} が担う。
 */
public interface RouteProviderPort {

    /**
     * 指定された区間に就航する全航海をルート候補として変換して返す。
     *
     * @param query ルート検索条件
     * @return ルート候補リスト（0 件の場合は空リスト）
     */
    List<RouteCandidate> findRoutes(RouteSearchQuery query);
}
