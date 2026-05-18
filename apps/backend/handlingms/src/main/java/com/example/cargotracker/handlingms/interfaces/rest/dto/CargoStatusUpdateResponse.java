package com.example.cargotracker.handlingms.interfaces.rest.dto;

/**
 * 貨物状態手動更新レスポンス DTO（US17）。
 */
public record CargoStatusUpdateResponse(
        String historyId,
        String trackingNumber,
        String newStatus,
        String unlocode) {
}
