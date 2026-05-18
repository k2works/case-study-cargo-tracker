package com.example.cargotracker.handlingms.domain.model.commands;

import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 貨物状態手動更新コマンド（US17）。
 *
 * <p>追跡管理者（ROLE_TRACKER）が貨物状態を手動で更新する。
 * IT5 では handlingms 内で暫定実装し、IT6 で trackingms に移管する予定（ADR-0012）。</p>
 *
 * <p>許可される状態（IT5 暫定）:</p>
 * <ul>
 *   <li>{@code IN_TRANSIT} - 輸送中</li>
 *   <li>{@code DELIVERED} - 引取済（CLAIM 以外の経路で完了する場合）</li>
 *   <li>{@code EXCEPTION} - 例外（IT7 例外処理で詳細化予定）</li>
 * </ul>
 *
 * @param activityId      履歴 ID（Aggregate Identifier として新規採番）
 * @param trackingNumber  追跡番号
 * @param newStatus       新しい貨物状態
 * @param location        現在位置（UN/LOCODE）
 * @param updatedAt       更新日時
 * @param operatorId      追跡管理者 ID
 */
public record UpdateCargoStatusCommand(
        @TargetEntityId String activityId,
        TrackingNumber trackingNumber,
        String newStatus,
        Location location,
        LocalDateTime updatedAt,
        HandlerId operatorId) {

    public static final Set<String> ALLOWED_STATUSES = Set.of("IN_TRANSIT", "DELIVERED", "EXCEPTION");

    public UpdateCargoStatusCommand {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(newStatus, "newStatus");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(operatorId, "operatorId");
        if (!ALLOWED_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "許可されていない状態への遷移です: " + newStatus
                            + "（許可: " + ALLOWED_STATUSES + "）");
        }
    }
}
