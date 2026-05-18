package com.example.cargotracker.handlingms.interfaces.rest.dto;

/**
 * 荷役作業登録レスポンス DTO。
 */
public record HandlingActivityResponse(
        String activityId,
        String trackingNumber,
        String handlingType,
        String unlocode,
        boolean unexpected) {
}
