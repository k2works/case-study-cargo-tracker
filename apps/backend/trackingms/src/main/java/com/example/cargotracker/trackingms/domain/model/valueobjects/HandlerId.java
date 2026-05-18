package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 操作者 ID（管理者・荷役作業員）を表す値オブジェクト。
 *
 * <p>認証コンテキスト（authms）と疎結合に保つため UUID 形式の検証は行わない。
 * trackingms では {@code TransportStatusUpdatedEvent.operatorId} に格納される。</p>
 */
public record HandlerId(String value) {

    public HandlerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("HandlerId は空文字にできません");
        }
    }
}
