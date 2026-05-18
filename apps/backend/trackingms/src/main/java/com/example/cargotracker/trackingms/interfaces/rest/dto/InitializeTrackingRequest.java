package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * US18 暫定: 内部初期化 API リクエスト。TI06 で {@code CargoTrackedEvent} 駆動化されると廃止される。
 */
public record InitializeTrackingRequest(
        String trackingNumber,
        String bookingId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDateTime estimatedArrival,
        String voyageNumber) {}
