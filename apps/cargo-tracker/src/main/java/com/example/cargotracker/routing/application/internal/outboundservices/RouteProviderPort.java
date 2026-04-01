package com.example.cargotracker.routing.application.internal.outboundservices;

import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;

import java.util.List;

/**
 * 外部ルートプロバイダーへのポートインターフェース。
 *
 * <p>実際のルート検索ロジック（外部 API 呼び出しや別マイクロサービスへの委譲）は
 * このインターフェースの実装クラスが担う。
 */
public interface RouteProviderPort {

    /**
     * 検索条件に合致するルート候補一覧を返す。
     *
     * @param query ルート検索条件
     * @return ルート候補リスト（0 件の場合は空リスト）
     */
    List<RouteCandidate> findRoutes(RouteSearchQuery query);
}
