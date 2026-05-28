package com.example.handlingms.domain.events;

import com.example.handlingms.domain.model.ClaimVerification;
import com.example.handlingms.domain.model.HandlingType;

import java.time.LocalDateTime;

/**
 * 荷役作業登録イベント（handlingms ローカル / US15・US16）。
 *
 * <p>{@link com.example.handlingms.domain.model.HandlingActivity} 集約が登録されたときに発行する。
 * Read Model {@code handling_activity} の投影トリガーとなる。</p>
 *
 * <p>本イベント受信後、handlingms の cross-service publisher が
 * shared モジュールの {@code com.example.shared.events.HandlingActivityRegisteredEvent} に
 * 変換して Kafka に発行し、trackingms に状態更新を依頼する（IT5 タスク 3.3、ADR-0011 適用）。</p>
 */
public record HandlingActivityRegisteredEvent(
        String activityId,
        String trackingNumber,
        HandlingType handlingType,
        LocalDateTime occurredAt,
        String unlocode,
        String voyageNumber,
        String handlerId,
        ClaimVerification claimVerification
) {
}
