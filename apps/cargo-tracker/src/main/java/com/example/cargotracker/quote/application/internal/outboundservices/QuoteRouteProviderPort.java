package com.example.cargotracker.quote.application.internal.outboundservices;

import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;

import java.util.List;

/**
 * 見積コンテキストのルート候補取得ポート。
 * 外部ルートプロバイダーへのアクセスを抽象化する。
 */
public interface QuoteRouteProviderPort {

    /**
     * 見積条件に合致するルート候補一覧を返す。
     * 外部システム障害時は空リストを返すか例外をスローする。
     *
     * @param condition 見積条件
     * @return ルート候補の一覧（0 件以上）
     */
    List<RouteOption> findRouteOptions(QuoteCondition condition);
}
