package com.example.trackingms.interfaces.rest.dto;

import java.util.List;

/**
 * 公開追跡照会の REST レスポンス DTO（US18 / ADR-0013）。
 *
 * <p>{@code GET /api/v1/public/tracking/{trackingNumber}?token=<JWT>} のレスポンス。
 * 荷主・荷受人向けに、サマリ・イベント履歴を一括返却する（例外履歴は IT6 例外実装後に追加）。</p>
 *
 * @param summary 追跡サマリ（現在状態・位置・到着予定）
 * @param events  追跡イベント履歴（時系列）
 */
public record PublicTrackingResponse(
        TrackingSummaryResponse summary,
        List<TrackingEventResponse> events
) {
}
