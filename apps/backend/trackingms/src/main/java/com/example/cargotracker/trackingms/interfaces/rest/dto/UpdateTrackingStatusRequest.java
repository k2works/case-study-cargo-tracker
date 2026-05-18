package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * TI06: 貨物状態手動更新 API (`PUT /api/v1/tracking/{tn}/status`) のリクエスト。
 *
 * <p>handlingms の旧エンドポイント {@code PUT /api/v1/handling/activities/{tn}/status}
 * から移管されたフォーマット。{@code TransportStatus} の遷移と更新日時・操作者を表す。</p>
 */
public record UpdateTrackingStatusRequest(
        String newStatus,
        String unlocode,
        LocalDateTime updatedAt,
        String operatorId) {}
