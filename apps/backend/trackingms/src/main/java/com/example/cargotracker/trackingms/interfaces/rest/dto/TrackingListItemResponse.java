package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * S16 追跡管理一覧の 1 行分の DTO。
 *
 * <p>一覧ビュー専用に最小限のフィールドのみ返す。詳細は {@code GET /api/v1/tracking/{tn}}
 * （公開）または S17 から取得する。</p>
 */
public record TrackingListItemResponse(
        String trackingNumber,
        String bookingId,
        String currentStatus,
        String currentUnlocode,
        String originUnlocode,
        String destinationUnlocode,
        LocalDateTime estimatedArrival,
        LocalDateTime deliveredAt,
        boolean misrouted,
        LocalDateTime lastEventAt,
        LocalDateTime updatedAt) {}
