package com.example.cargotracker.archfixture.violating.domain.model;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;

/** 違反フィクスチャ: 許可リストに無い Axon の型（設定系）をドメインが持つ。 */
public class UnlistedAxonTypeAggregate {

    private final AxonServerConnectionManager connectionManager;

    public UnlistedAxonTypeAggregate(AxonServerConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public String context() {
        return connectionManager.getDefaultContext();
    }
}
