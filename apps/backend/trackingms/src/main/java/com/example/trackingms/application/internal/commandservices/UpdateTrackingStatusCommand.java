package com.example.trackingms.application.internal.commandservices;

/**
 * 貨物状態手動更新コマンド
 */
public record UpdateTrackingStatusCommand(
        String trackingNumber,
        String newStatus
) {}
