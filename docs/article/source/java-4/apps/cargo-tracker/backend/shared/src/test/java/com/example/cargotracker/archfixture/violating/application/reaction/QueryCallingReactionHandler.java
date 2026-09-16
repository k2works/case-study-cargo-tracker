package com.example.cargotracker.archfixture.violating.application.reaction;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

/** 違反フィクスチャ: Reaction Handler が同期クエリを待つ（Processing Group が止まる）。 */
public class QueryCallingReactionHandler {

    private final QueryGateway queryGateway;

    public QueryCallingReactionHandler(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public Object on(Object event) {
        return queryGateway.query(event, Object.class).join();
    }
}
