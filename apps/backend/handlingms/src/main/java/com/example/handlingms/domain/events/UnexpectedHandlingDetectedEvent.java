package com.example.handlingms.domain.events;

import com.example.handlingms.domain.model.HandlingType;

import java.time.LocalDateTime;

/**
 * 予定外荷役作業検知イベント（US15 / IT5 3.2）。
 *
 * <p>{@code CargoSnapshot} ACL に保持された予定（origin / destination / itinerary）と
 * 異なる場所・種別で荷役が記録された場合に、{@link HandlingActivityRegisteredEvent} と
 * 同時に発行される warning イベント。記録自体は許容される（実運用では予定外場所での
 * 荷役も発生しうるため）が、追跡管理者への通知や監査ログのトリガーとなる。</p>
 *
 * <p>domain-model.md HandlingActivity 不変条件:
 * <em>「cargoSnapshot.isExpectedHandling(type, location) が false の場合は警告イベントを発行
 * （記録は許容）」</em>に基づく実装。</p>
 *
 * @param activityId     登録された荷役活動 ID
 * @param trackingNumber 追跡番号
 * @param handlingType   荷役種別
 * @param unlocode       実際の発生場所
 * @param occurredAt     発生時刻
 * @param reason         予定外判定の理由（"origin 不一致" / "destination 不一致" など）
 */
public record UnexpectedHandlingDetectedEvent(
        String activityId,
        String trackingNumber,
        HandlingType handlingType,
        String unlocode,
        LocalDateTime occurredAt,
        String reason
) {
}
