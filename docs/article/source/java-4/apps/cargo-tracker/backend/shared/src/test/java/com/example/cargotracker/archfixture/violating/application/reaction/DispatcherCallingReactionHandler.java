package com.example.cargotracker.archfixture.violating.application.reaction;

import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;

/**
 * 違反フィクスチャ: Reaction Handler が {@link QueryDispatcher} 越しに同期クエリを待つ。
 *
 * <p><b>{@code QueryGateway} を直に持つ形とは別に要る。</b> IT5 で送り口を共有カーネルへ
 * 寄せたので、包み越しの呼び出しが本番で現れうる。違反フィクスチャが旧来の形だけだと、
 * 広げた規則が書き間違いで空振りしていてもメタテストは緑になる
 * （「自作 lint のフィクスチャは実コードの形で作る」）。</p>
 */
public class DispatcherCallingReactionHandler {

    private final QueryDispatcher queries;

    public DispatcherCallingReactionHandler(QueryDispatcher queries) {
        this.queries = queries;
    }

    public Object on(Object event) {
        return queries.query(event, Object.class);
    }
}
