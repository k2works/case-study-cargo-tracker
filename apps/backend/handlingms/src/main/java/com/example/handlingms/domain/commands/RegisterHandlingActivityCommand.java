package com.example.handlingms.domain.commands;

import com.example.handlingms.domain.model.ClaimVerification;
import com.example.handlingms.domain.model.HandlingType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;

/**
 * 荷役作業登録コマンド（US15・US16 / IT5 タスク 3.2）。
 *
 * <p>荷役作業員が現場で記録する荷役活動を {@link com.example.handlingms.domain.model.HandlingActivity}
 * 集約に新規登録する。不変条件:</p>
 * <ul>
 *   <li>{@code handlingType = LOAD / UNLOAD} のとき {@code voyageNumber} は必須</li>
 *   <li>{@code handlingType = CLAIM} のとき {@code claimVerification} は必須</li>
 *   <li>その他では {@code voyageNumber} / {@code claimVerification} は任意</li>
 * </ul>
 *
 * <p>受理されると {@link com.example.handlingms.domain.events.HandlingActivityRegisteredEvent}
 * が発行され、trackingms に shared モジュールの
 * {@code com.example.shared.events.HandlingActivityRegisteredEvent} として cross-service 配信される
 * （IT5 タスク 3.3）。受信側 trackingms は {@code UpdateTransportStatusCommand} で
 * {@code TrackingActivity} の状態を更新する。</p>
 */
public record RegisterHandlingActivityCommand(
        @TargetAggregateIdentifier String activityId,
        String trackingNumber,
        HandlingType handlingType,
        LocalDateTime occurredAt,
        String unlocode,
        String voyageNumber,
        String handlerId,
        ClaimVerification claimVerification
) {
}
