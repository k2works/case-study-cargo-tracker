package com.example.cargotracker.trackingms.domain.model.commands;

import com.example.cargotracker.trackingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 貨物状態手動更新コマンド（US17 移管後・TI06）。
 *
 * <p>営業担当者・管理者が任意の {@link TransportStatus} 間で状態を遷移させる。
 * 発行される {@code TransportStatusUpdatedEvent} の {@code source} は常に
 * {@link com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource#MANUAL}。</p>
 *
 * @param trackingNumber 追跡番号（Aggregate 識別子）
 * @param newStatus      遷移先状態
 * @param location       状態更新時の位置（任意。null 可とせず明示）
 * @param updatedAt      更新日時（過去または現在）
 * @param operatorId     操作者 ID
 */
public record UpdateTransportStatusCommand(
        @TargetEntityId String trackingNumber,
        TransportStatus newStatus,
        Location location,
        LocalDateTime updatedAt,
        HandlerId operatorId) {

    public UpdateTransportStatusCommand {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(operatorId, "operatorId");
    }
}
